# ESP8266 RC Car Controller

This folder contains the Arduino code for the ESP8266-based remote-controlled car that works with the Android control app.

## Hardware Requirements

### Components
- NodeMCU ESP8266 board
- L298N Motor Driver Module
- 2 or 4 DC motors (recommended 3-6V motors)
- HC-SR04 Ultrasonic Distance Sensor (for obstacle avoidance)
- Car chassis with wheels
- 18650 Battery holder with 2-3 batteries (or other suitable power source)
- Jumper wires
- Optional: LED indicators

### Connections

#### Motor Driver (L298N) to ESP8266
| L298N Pin | ESP8266 Pin | Function |
|-----------|-------------|----------|
| IN1       | D1 (GPIO5)  | Left motor direction control 1 |
| IN2       | D2 (GPIO4)  | Left motor direction control 2 |
| IN3       | D3 (GPIO0)  | Right motor direction control 1 |
| IN4       | D4 (GPIO2)  | Right motor direction control 2 |
| ENA       | D5 (GPIO14) | Left motor PWM speed control |
| ENB       | D6 (GPIO12) | Right motor PWM speed control |
| GND       | GND         | Common ground |

#### Ultrasonic Sensor (HC-SR04) to ESP8266
| HC-SR04 Pin | ESP8266 Pin | Function |
|-------------|-------------|----------|
| VCC         | 5V          | Power supply |
| Trig        | D7 (GPIO13) | Trigger input |
| Echo        | D8 (GPIO15) | Echo output |
| GND         | GND         | Common ground |

#### Power Supply
- Connect the L298N module's power input to the battery pack (7-12V)
- The ESP8266 can be powered through the L298N 5V output or a separate USB power bank

## Software Implementation

### WiFi Setup
The ESP8266 creates a WiFi access point with:
- SSID: "RC_CAR_AP" (can be modified in code)
- Password: "password123" (can be modified in code)
- Default IP: 192.168.4.1

### Motor Control
The code implements:
- PWM-based speed control for both motors
- Direction control for forward, backward, left, right movements
- Smooth speed ramping for better control

### Communication Protocol
- TCP socket server on port 80
- Accepts commands in the format: `leftPWM,rightPWM`
  - Values range from -255 to 255
  - Positive values move the motor forward
  - Negative values move the motor backward
  - Zero stops the motor
- Special commands:
  - "OBSTACLE_ON" - Activates obstacle avoidance mode
  - "OBSTACLE_OFF" - Deactivates obstacle avoidance mode

### Obstacle Avoidance
When enabled, the car:
1. Continuously measures distance to obstacles using the ultrasonic sensor
2. If an obstacle is detected within the threshold distance (20cm by default):
   - Stops forward movement
   - Backs up slightly
   - Turns to avoid the obstacle
   - Resumes forward movement

## Installation Instructions

1. Install the Arduino IDE
2. Add ESP8266 board support to Arduino IDE:
   - Go to File > Preferences
   - Add `http://arduino.esp8266.com/stable/package_esp8266com_index.json` to Additional Board Manager URLs
   - Go to Tools > Board > Board Manager
   - Search for and install "esp8266"
3. Select NodeMCU 1.0 board from Tools > Board menu
4. Open the RC_Car.ino file in Arduino IDE
5. Modify WiFi SSID and password if desired
6. Upload the code to your ESP8266

## Troubleshooting

### Connection Issues
- Ensure your phone is connected to the ESP8266's WiFi network
- Verify the ESP8266 IP address matches the one in the Android app (192.168.4.1)

### Motor Issues
- Check wiring connections to L298N
- Verify battery voltage is sufficient
- Test each motor individually with simple test code

### Obstacle Avoidance Issues
- Check ultrasonic sensor connections
- Verify proper positioning of sensor on car chassis
- Adjust the threshold distance in code if needed

## Power Management
- The ESP8266 consumes approximately 70-80mA when active
- Each motor can draw 100-500mA depending on load
- Total system can draw 300-1000mA
- Recommended battery capacity: minimum 2000mAh for 2-3 hours of operation

## Future Improvements
- Add battery level monitoring and reporting
- Implement PID control for more precise movement
- Support for additional sensors (line following, etc.)

