package com.vonreyher.medicationtracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class EmaException(message: String) : Exception(message)

/** A medicinal product from the EMA Product Management Service (PMS). */
data class EmaProduct(
    val id: String,
    val name: String,
    val doseForm: String?,
    val legalStatus: String?,
    val classification: String?,
    val ingredients: List<String>
)

/**
 * Client for the public EMA PMS API (FHIR R5), https://api.pms.ema.europa.eu/public/v1.
 *
 * Every request needs an OAuth2 client-credentials token; the token endpoint and the credentials
 * are issued by the EMA on registration. The API has no PZN search, so products are looked up by name.
 */
class EmaClient(private val credentials: EmaCredentials) {

    private var token: String? = null
    private var tokenExpiresAt = 0L

    /** Searches products by name, trying shorter forms of the name if the full one finds nothing. */
    suspend fun searchProducts(name: String, limit: Int = 5): List<EmaProduct> = withContext(Dispatchers.IO) {
        if (!credentials.isComplete) throw EmaException("EMA-Zugang ist nicht eingerichtet")

        val words = name.split(' ', '-', ',').filter { it.isNotBlank() }
        val queries = listOf(name.trim(), words.take(2).joinToString(" "), words.firstOrNull().orEmpty())
            .filter { it.length >= 3 }
            .distinct()

        for (query in queries) {
            val bundle = get("/MedicinalProductDefinition?name=${query.urlEncoded()}&_count=$limit")
            val products = bundle.resources("MedicinalProductDefinition").map { it.toProduct() }
            if (products.isNotEmpty()) {
                return@withContext products.map { product ->
                    product.copy(ingredients = runCatching { ingredientsOf(product.id) }.getOrDefault(emptyList()))
                }
            }
        }
        emptyList()
    }

    private fun ingredientsOf(productId: String): List<String> =
        get("/MedicinalProductDefinition/${productId.urlEncoded()}/\$everything")
            .resources("Ingredient")
            .mapNotNull { ingredient ->
                val substance = ingredient.optJSONObject("substance") ?: return@mapNotNull null
                val name = substance.optJSONObject("code")?.optJSONObject("concept")?.text()
                    ?: return@mapNotNull null
                val strength = substance.optJSONArray("strength")?.optJSONObject(0)?.strengthText()
                listOfNotNull(name, strength).joinToString(" ")
            }
            .distinct()

    private fun get(path: String): JSONObject {
        val response = request(
            url = BaseUrl + path,
            headers = mapOf(
                "Authorization" to "Bearer ${accessToken()}",
                "Accept" to "application/fhir+json"
            )
        )
        return JSONObject(response)
    }

    @Synchronized
    private fun accessToken(): String {
        token?.takeIf { System.currentTimeMillis() < tokenExpiresAt }?.let { return it }

        val form = buildList {
            add("grant_type" to "client_credentials")
            add("client_id" to credentials.clientId)
            add("client_secret" to credentials.clientSecret)
            if (credentials.scope.isNotBlank()) add("scope" to credentials.scope)
        }.joinToString("&") { (key, value) -> "$key=${value.urlEncoded()}" }

        val json = try {
            JSONObject(
                request(
                    url = credentials.tokenUrl,
                    method = "POST",
                    body = form,
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded")
                )
            )
        } catch (e: JSONException) {
            throw EmaException("Ungültige Antwort vom Token-Endpunkt")
        }
        val accessToken = json.optString("access_token").ifBlank {
            throw EmaException("Kein Token erhalten – bitte die Zugangsdaten prüfen")
        }
        // Renew a minute early; EMA tokens are valid for one hour.
        tokenExpiresAt = System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 60) * 1000
        token = accessToken
        return accessToken
    }

    private fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): String {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw EmaException("Ungültige Adresse: $url")
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code == 401 || code == 403 -> throw EmaException("Zugriff verweigert ($code) – bitte die EMA-Zugangsdaten prüfen")
                code !in 200..299 -> throw EmaException("Die EMA-Datenbank hat mit Fehler $code geantwortet")
            }
            return text
        } catch (e: IOException) {
            throw EmaException("Keine Verbindung zur EMA-Datenbank")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BaseUrl = "https://api.pms.ema.europa.eu/public/v1"
    }
}

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

private fun JSONObject.resources(type: String): List<JSONObject> {
    val entries = optJSONArray("entry") ?: return emptyList()
    return (0 until entries.length())
        .mapNotNull { entries.optJSONObject(it)?.optJSONObject("resource") }
        .filter { it.optString("resourceType") == type }
}

/** Display text of a FHIR CodeableConcept, falling back to its code. */
private fun JSONObject.text(): String? {
    optString("text").takeIf { it.isNotBlank() }?.let { return it }
    val codings = optJSONArray("coding") ?: return null
    val all = (0 until codings.length()).mapNotNull { codings.optJSONObject(it) }
    return all.firstNotNullOfOrNull { it.optString("display").takeIf(String::isNotBlank) }
        ?: all.firstNotNullOfOrNull { it.optString("code").takeIf(String::isNotBlank) }
}

private fun JSONObject.toProduct(): EmaProduct {
    val doseForm = optJSONArray("extension").objects()
        .firstOrNull { it.optString("url").endsWith("authorisedDoseForm") }
        ?.optJSONObject("valueCodeableConcept")?.text()
        ?: optJSONObject("combinedPharmaceuticalDoseForm")?.text()
    return EmaProduct(
        id = optString("id"),
        name = optJSONArray("name")?.optJSONObject(0)?.optString("productName")?.ifBlank { null }
            ?: "Unbenanntes Präparat",
        doseForm = doseForm,
        legalStatus = optJSONObject("legalStatusOfSupply")?.text(),
        classification = optJSONArray("classification").objects().firstNotNullOfOrNull { it.text() },
        ingredients = emptyList()
    )
}

private fun JSONObject.strengthText(): String? {
    optJSONObject("presentationRatio")?.let { ratio ->
        val numerator = ratio.optJSONObject("numerator")?.quantityText() ?: return@let
        val denominator = ratio.optJSONObject("denominator")?.quantityText()
        return if (denominator == null || denominator == "1" || denominator.startsWith("1 ")) numerator
        else "$numerator / $denominator"
    }
    return optJSONObject("presentationQuantity")?.quantityText()
}

private fun JSONObject.quantityText(): String? {
    if (!has("value")) return null
    val value = optDouble("value").let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
    val unit = optString("unit").ifBlank { optString("code") }
    return listOf(value, unit).filter { it.isNotBlank() }.joinToString(" ")
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
