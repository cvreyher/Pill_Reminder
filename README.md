# MedicationTracker

**An open-source Android medication reminder and pill tracking app built with Kotlin and Jetpack Compose.**

MedicationTracker aims to make managing daily medications simple and reliable. Organize your medications, create personalized medication schedules, receive timely reminders, and keep track of every dose.

Never forget to take your medication again.

> **Project status:** Early development. The Android project foundation is being established, and core medication management features are on the roadmap.

## Features

MedicationTracker is being developed around the essential needs of everyday medication management.

- **Medication Management** – Add, organize and manage your medications in one place.
- **Medication Reminders** – Set personalized reminders for when to take your medication.
- **Pill Tracker** – Keep track of your daily medication intake.
- **Medication Schedule** – Organize medications by time, frequency and dosage.
- **Dose Tracking** – Record taken and missed doses.
- **Intake History** – Review your medication history and daily progress.
- **Daily Overview** – Get a clear overview of your upcoming and completed medication reminders.

*These are planned features and are not yet all implemented.*

## Screenshots

Screenshots will be added as the application develops.

## Tech Stack

MedicationTracker is built using modern native Android development technologies.

| Technology | Purpose |
|---|---|
| Kotlin | Primary programming language |
| Jetpack Compose | Native Android UI |
| Material 3 | Design system |
| Gradle Kotlin DSL | Build configuration |
| MVVM | Application architecture |
| Android SDK | Native Android platform |

## Project Structure

The project follows a modular and maintainable Android application structure.

```text
MedicationTracker/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/vonreyher/medicationtracker/
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## Getting Started

### Requirements

- Android Studio or IntelliJ IDEA with Android support
- JDK 17 or compatible configured JDK
- Android SDK
- Git

### Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/MedicationTracker.git

cd MedicationTracker
```

### Build the Application

On macOS or Linux:

```bash
./gradlew assembleDebug
```

On Windows:

```bash
gradlew.bat assembleDebug
```

### Run the Application

Open the project in Android Studio, wait for Gradle synchronization, select an Android emulator or connected device, and run the `app` configuration.

## Development

The application is developed as a native Android application using Kotlin and Jetpack Compose.

The development priorities are:

1. Establish a clean and maintainable application architecture.
2. Build the medication management interface.
3. Implement medication schedules and dosage configuration.
4. Develop reliable local medication notifications.
5. Add persistent storage and intake history.
6. Improve accessibility and overall user experience.

## Roadmap

- [ ] Medication creation and management
- [ ] Custom medication schedules
- [ ] Daily medication reminders
- [ ] Notification and alarm system
- [ ] Taken / skipped dose tracking
- [ ] Medication intake history
- [ ] Local database persistence
- [ ] Refill and remaining-pill tracking
- [ ] Multiple reminder frequencies
- [ ] Dashboard and daily overview

## Contributing

Contributions, suggestions and bug reports are welcome.

If you have an idea for improving medication reminders or making medication management easier, feel free to open an issue or submit a pull request.

## License

License information will be added.

---

**MedicationTracker** – A simple, reliable medication reminder and pill tracker for Android.

Built with Kotlin and Jetpack Compose.
