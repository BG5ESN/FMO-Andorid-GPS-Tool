# FMO Android GPS Tool

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-1.5.0-blue.svg)](https://developer.android.com/jetpack/compose)

Chinese version: [README.md](README.md)

A minimalist Android tool for discovering FMO devices on the same local network and periodically writing mobile GPS coordinates to FMO via WebSocket.

## Project Overview
FMO Android GPS Tool is an Android application designed for vehicle/mobile scenarios. It can run continuously in the background, automatically discover FMO devices on the local network, and periodically send the phone's GPS coordinates to the device.

### Core Objectives
- Get the current location of the phone (WGS84 coordinate system)
- Write coordinates to FMO via WebSocket using the GEO interface
- Support periodic synchronization from 1 to 30 minutes
- Support automatic LAN discovery (mDNS: `fmo.local`)
- Support continuous background operation (foreground service + persistent notification)

### Use Cases
- Vehicle navigation systems requiring real-time location updates
- Mobile devices needing to periodically send location information to FMO devices
- Automation scenarios requiring "stable synchronization after startup"

## Features

### Core Features
- **Device Discovery**: Automatically discover FMO devices on the local network via mDNS
- **Coordinate Synchronization**: Periodically obtain phone GPS coordinates and send them to FMO devices
- **Background Operation**: Use foreground service to ensure continuous app operation in the background
- **Status Notification**: Persistent notification displaying current synchronization status
- **Configuration Persistence**: App settings are automatically saved and retained after restart

### Technical Features
- **Modern UI**: Modern interface built with Jetpack Compose
- **MVVM Architecture**: Clear architectural separation for easy maintenance and testing
- **Coroutine Support**: Use Kotlin coroutines for asynchronous operations
- **Data Persistence**: Use DataStore for storing app settings
- **Permission Management**: Comprehensive Android permission request and handling

### Permission Requirements
The app requires the following permissions:
- **Location Permission**: To obtain the device's current location
- **Notification Permission**: To display background operation status notifications
- **Foreground Service Permission**: To ensure the app continues running in the background

On first launch, the app will guide users to grant the required permissions.

### Synchronization Process
1. User clicks "Start Location"
2. App checks permissions and Host settings
3. Establish WebSocket connection to FMO device
4. Immediately obtain and send one location
5. Enter periodic synchronization task
6. Update status and coordinate display after each synchronization
7. Stop synchronization when user clicks "Stop Location"

## Technical Architecture

### Project Structure
```
app/src/main/java/com/example/fmogeoapp/
├── MainActivity.kt              # Main Activity, handles permission requests
├── data/                        # Data layer
│   ├── SettingsDataStore.kt     # Settings data storage
│   └── model/AppSettings.kt     # App settings model
├── network/                     # Network layer
│   └── FmoGeoProtocol.kt        # FMO GEO protocol implementation
├── service/                     # Service layer
│   ├── SyncForegroundService.kt # Foreground synchronization service
│   ├── DiscoveryService.kt      # Device discovery service
│   ├── LocationService.kt       # Location service
│   ├── SyncServiceBinder.kt     # Service binder
│   └── UnifiedServiceState.kt   # Unified service state
├── ui/                          # UI layer
│   ├── screens/                 # Screen components
│   │   ├── MainScreen.kt        # Main interface
│   │   └── SettingsScreen.kt    # Settings interface
│   └── theme/                   # Theme and styles
└── viewmodel/                   # ViewModel layer
    └── MainViewModel.kt         # Main interface ViewModel
```

## Protocol Specification

### FMO GEO Protocol Overview
The FMO GEO protocol uses WebSocket for communication, with messages in JSON format.

### Message Format
```json
{
  "type": "config",
  "subType": "setCordinate",
  "data": {
    "latitude": 31.2304,
    "longitude": 121.4737
  },
  "code": 0
}
```

### Supported Message Types
- `setCordinate`: Set coordinates
- `getCordinate`: Get coordinates
- `setCordinateResponse`: Set coordinate response
- `getCordinateResponse`: Get coordinate response

### Coordinate System
- Uses WGS84 coordinate system
- Latitude range: -90.0 to 90.0
- Longitude range: -180.0 to 180.0

## Building and Installation

### Prerequisites
- Android Studio (latest version recommended)
- Android SDK 24 or higher
- Java 11 or higher

### Build Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/BG5ESN/FMO-Andorid-GPS-Tool.git
   cd FMO-Andorid-GPS-Tool
   ```

2. Open the project in Android Studio

3. Build the project:
   - Click "Build" → "Make Project" in Android Studio
   - Or use Gradle command: `./gradlew assembleDebug`

4. Install on device:
   - Connect Android device via USB with USB debugging enabled
   - Run: `./gradlew installDebug`

### Configuration
1. **Host Configuration**: Set the FMO device host (IP address or `fmo.local` for mDNS discovery)
2. **Sync Interval**: Configure synchronization interval (1-30 minutes)
3. **Location Precision**: Choose between fine or coarse location accuracy

## Usage Guide

### Initial Setup
1. Install the app on your Android device
2. Launch the app and grant required permissions
3. Configure the FMO device host in settings
4. Set your preferred synchronization interval

### Operation
1. **Start Synchronization**: Tap "Start Location" on the main screen
2. **Monitor Status**: Check the notification for current synchronization status
3. **View Coordinates**: Current coordinates are displayed on the main screen
4. **Stop Synchronization**: Tap "Stop Location" when done

### Settings
- **Host**: FMO device address (e.g., `192.168.1.100` or `fmo.local`)
- **Sync Interval**: How often to send coordinates (1-30 minutes)
- **Location Precision**: Fine (GPS) or coarse (network) location
- **Auto Discovery**: Enable/disable mDNS device discovery

## Development

### Dependencies
- **Kotlin**: 1.9.0
- **Jetpack Compose**: 1.5.0
- **AndroidX Libraries**: Core KTX, Lifecycle, DataStore
- **Networking**: OkHttp for WebSocket communication
- **mDNS**: JmDNS for device discovery

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Maintain consistent indentation (4 spaces)

### Testing
- Unit tests in `app/src/test/`
- Instrumentation tests in `app/src/androidTest/`

## Contributing
Contributions of any kind are welcome!

### How to Contribute
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

### Code Review Guidelines
- Ensure code follows project conventions
- Include appropriate documentation
- Add tests for new functionality
- Update README if needed

## Troubleshooting

### Common Issues
1. **Connection Failed**: Check if FMO device is on the same network
2. **Location Not Updating**: Verify location permissions are granted
3. **App Stops in Background**: Ensure foreground service permission is granted
4. **mDNS Discovery Not Working**: Verify network supports mDNS (Bonjour)

### Logs and Debugging
- Enable debug logging in the app
- Check Android Logcat for error messages
- Verify WebSocket connection status

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contact
For any questions or suggestions, please contact:
- Email: xifengzui@yeah.net

## Acknowledgments
- Thanks to the Android and Kotlin communities
- Inspired by FMO device integration needs
- Built with Jetpack Compose for modern UI

## Version History
- **v1.03**: Bug fixes and stability improvements
- **v1.02**: Added mDNS device discovery
- **v1.01**: Improved background service reliability
- **v1.00**: Initial release with basic functionality