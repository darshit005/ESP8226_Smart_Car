# Car Control App

## Overview
This Android application provides multiple control interfaces for a remote-controlled car using an ESP8266 WiFi module. The app connects to the ESP8266 via WiFi and sends control commands to drive the car.

## Features

### Multiple Control Methods
- **Virtual Joystick**: Intuitive joystick interface for precise control
- **Gyroscope Control**: Tilt your phone to control the car
- **Voice Commands**: Control the car with voice in both English and Gujarati
- **Obstacle Avoidance Mode**: Automatic obstacle detection and avoidance

### Advanced Functionality
- **Return-to-Home**: The car can retrace its path back to the starting point
- **Command History**: Tracks movement commands to enable return function
- **Disco Mode**: Fun movement pattern for entertainment
- **Reset Function**: Quickly reset all functions and stop the car

### UI Features
- **Immersive Fullscreen**: Distraction-free control experience
- **Animated Background**: Attractive gradient animations
- **Connection Status**: Clear indication of connection status
- **Toggle Controls**: Easy switching between different control methods

## Control Modes

### Joystick Control
The virtual joystick provides precise control with variable speed based on distance from center:
- Forward/backward motion and turns with one intuitive control
- Specialized zones for better turning radius and movement control
- Auto-centering when released to stop the car

### Gyroscope Control
Use your phone's accelerometer to control the car:
- Tilt forward/backward for forward/reverse
- Tilt left/right to turn
- Visual feedback shows tilt position on screen

### Voice Commands
Support for both English and Gujarati voice commands:
- **Forward**: "forward", "go forward", "move forward", "go ahead", "straight", "આગળ", etc.
- **Backward**: "backward", "go backward", "move backward", "back", "reverse", "પાછળ", etc.
- **Left**: "left", "turn left", "go left", "ડાબી", "ડાબી બાજું", etc.
- **Right**: "right", "turn right", "go right", "જમણી બાજું", "જમણી", etc.
- **Stop**: "stop", "halt", "brake", "freeze", "ઉભો રે", etc.
- **Circle**: "circle", "spin", "rotate", "turn around"
- **Disco**: "disco", "disco mode", "party", "dance"

### Obstacle Avoidance
Automatic detection and avoidance of obstacles:
- Ultrasonic sensor on car detects obstacles
- Car automatically changes direction to avoid collisions
- Toggle on/off from the app interface

## Technical Details

### Communication Protocol
The app communicates with the ESP8266 using a simple text-based protocol:
- PWM commands are sent as "leftMotor,rightMotor" (values from -255 to 255)
- Special commands for enabling/disabling features

### Connectivity
- Connects to ESP8266 hotspot (default IP: 192.168.4.1, port: 80)
- Automatic reconnection handling
- Visual connection status indication

## Installation
1. Clone this repository
2. Open in Android Studio
3. Build and install on your Android device

## Hardware Requirements
- Android phone with accelerometer and microphone
- ESP8266-based RC car (see Arduino folder for hardware setup)

## License
[Insert your license information here]

## Credits
Developed by [Your Name] 