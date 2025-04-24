/*
 * ESP8266 Car Controller with L298N Motor Driver
 * Features:
 * - WiFi AP mode for direct connection without internet
 * - Joystick and gyroscope controls from Android app
 * - Voice command control from Android app
 * - Ultrasonic sensor for obstacle detection
 * - Servo motor for scanning surroundings
 * - Obstacle avoidance mode
 */

#include <ESP8266WiFi.h>
#include <Servo.h>

// WiFi configuration
const char* ssid = "ESP8266_CAR";     // WiFi SSID
const char* password = "123456789";   // WiFi password

// Network configuration
WiFiServer server(80);                   // HTTP server on port 80
WiFiClient client;                       // Client object

// Pin configuration for L298N Motor Driver
const int IN1 = D1;   // Right motor direction pin 1
const int IN2 = D2;   // Right motor direction pin 2
const int IN3 = D5;   // Left motor direction pin 1
const int IN4 = D6;   // Left motor direction pin 2
const int ENA = D3;   // Right motor PWM speed control
const int ENB = D7;   // Left motor PWM speed control

// Pin configuration for Ultrasonic sensor (HC-SR04)
const int TRIG_PIN = D0;  // Trigger pin
const int ECHO_PIN = D8;  // Echo pin

// Pin configuration for Servo motor
const int SERVO_PIN = D4;  // Servo PWM control pin

// Constants for obstacle avoidance
const int OBSTACLE_THRESHOLD_CM = 30;  // Distance threshold to detect obstacles
const int SERVO_CENTER = 90;           // Servo center position (degrees)
const int SERVO_LEFT = 0;              // Servo left position (degrees)
const int SERVO_RIGHT = 180;           // Servo right position (degrees)

// Variables
Servo servoMotor;                      // Servo object
int leftPWM = 0;                       // Left motor PWM (-255 to 255)
int rightPWM = 0;                      // Right motor PWM (-255 to 255)
bool obstacleAvoidanceMode = false;    // Obstacle avoidance mode flag
long lastObstacleDetectionTime = 0;    // Time of last obstacle detection
float distance = 0;                    // Current distance from ultrasonic sensor
String command = "";                   // Received command string

// Serial communication buffer
const int BUFFER_SIZE = 64;
char buffer[BUFFER_SIZE];
int bufferIndex = 0;

void setup() {
  // Initialize serial communications
  Serial.begin(115200);
  
  // Initialize motor control pins
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);
  pinMode(ENA, OUTPUT);
  pinMode(ENB, OUTPUT);
  
  // Initialize ultrasonic sensor pins
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  
  // Initialize servo
  servoMotor.attach(SERVO_PIN);
  servoMotor.write(SERVO_CENTER);  // Center the servo
  
  // Stop motors initially
  setMotorPWM(0, 0);
  
  // Setup WiFi Access Point
  setupWiFiAP();
}

void loop() {
  // Check for client connections
  if (!client || !client.connected()) {
    client = server.available();
    if (client) {
      Serial.println("New client connected");
      bufferIndex = 0;
    }
  }
  
  // Handle client communication
  if (client && client.connected()) {
    while (client.available()) {
      char c = client.read();
      
      // Add character to buffer
      if (bufferIndex < BUFFER_SIZE - 1) {
        buffer[bufferIndex++] = c;
      }
      
      // Process command on newline or buffer full
      if (c == '\n' || bufferIndex >= BUFFER_SIZE - 1) {
        buffer[bufferIndex] = '\0';  // Null terminate
        command = String(buffer);
        
        // Process received command
        processCommand(command);
        
        // Reset buffer
        bufferIndex = 0;
      }
    }
  }
  
  // Check for obstacles if obstacle avoidance is active or if driving forward
  if (obstacleAvoidanceMode || (leftPWM > 0 && rightPWM > 0)) {
    distance = getDistanceCm();
    // Print distance to serial monitor for debugging
    Serial.print("Distance: ");
    Serial.print(distance);
    Serial.println(" cm");
    
    if (distance <= OBSTACLE_THRESHOLD_CM && distance > 0) {
      handleObstacleDetection();
    } else if (obstacleAvoidanceMode) {
      // In obstacle avoidance mode, keep moving forward if no obstacle
      setMotorPWM(255, 255);
    }
  }
}

// Set up WiFi Access Point
void setupWiFiAP() {
  Serial.println("Setting up WiFi Access Point...");
  WiFi.mode(WIFI_AP);
  WiFi.softAP(ssid, password);
  
  IPAddress myIP = WiFi.softAPIP();
  Serial.print("AP IP address: ");
  Serial.println(myIP);
  
  server.begin();
  Serial.println("Server started");
}

// Process incoming command from the mobile app
void processCommand(String cmd) {
  cmd.trim();  // Remove any whitespace/newlines
  
  // Print received command to serial monitor
  Serial.print("Received command: ");
  Serial.println(cmd);
  
  // Check for special commands
  if (cmd == "OBSTACLE_ON") {
    obstacleAvoidanceMode = true;
    servoMotor.write(SERVO_CENTER);  // Center the servo
    Serial.println("Obstacle avoidance mode activated");
    return;
  } 
  else if (cmd == "OBSTACLE_OFF") {
    obstacleAvoidanceMode = false;
    servoMotor.write(SERVO_CENTER);  // Center the servo
    setMotorPWM(0, 0);  // Stop motors
    Serial.println("Obstacle avoidance mode deactivated");
    return;
  }
  
  // Process regular motor commands in format "leftPWM,rightPWM"
  int commaIndex = cmd.indexOf(',');
  if (commaIndex > 0 && !obstacleAvoidanceMode) {
    // Extract left and right PWM values
    leftPWM = cmd.substring(0, commaIndex).toInt();
    rightPWM = cmd.substring(commaIndex + 1).toInt();
    
    // Apply PWM to motors
    setMotorPWM(leftPWM, rightPWM);
  }
}

// Set motor PWM values (-255 to 255 for each motor)
void setMotorPWM(int left, int right) {
  // Constrain PWM values to valid range
  left = constrain(left, -255, 255);
  right = constrain(right, -255, 255);
  
  // Set left motor direction and PWM
  if (left > 0) {
    digitalWrite(IN3, HIGH);
    digitalWrite(IN4, LOW);
    analogWrite(ENB, left);
  } 
  else if (left < 0) {
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, HIGH);
    analogWrite(ENB, abs(left));
  } 
  else {
    digitalWrite(IN3, LOW);
    digitalWrite(IN4, LOW);
    analogWrite(ENB, 0);
  }
  
  // Set right motor direction and PWM
  if (right > 0) {
    digitalWrite(IN1, HIGH);
    digitalWrite(IN2, LOW);
    analogWrite(ENA, right);
  } 
  else if (right < 0) {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, HIGH);
    analogWrite(ENA, abs(right));
  } 
  else {
    digitalWrite(IN1, LOW);
    digitalWrite(IN2, LOW);
    analogWrite(ENA, 0);
  }
  
  // Output to serial monitor for debugging
  Serial.print("Left PWM: ");
  Serial.print(left);
  Serial.print(", Right PWM: ");
  Serial.println(right);
}

// Get distance in centimeters from ultrasonic sensor
float getDistanceCm() {
  // Clear the trigger pin
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  
  // Send a 10μs pulse to trigger the sensor
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  
  // Measure the echo pulse duration
  long duration = pulseIn(ECHO_PIN, HIGH, 30000);  // Timeout after 30ms
  
  // Calculate distance (sound speed is 343m/s or 0.0343cm/μs)
  // Division by 2 because sound travels to the object and back
  float distance = duration * 0.0343 / 2;
  
  // If distance is out of range, return a very large value
  if (distance <= 0 || distance > 400) {
    return 9999;
  }
  
  return distance;
}

// Handle obstacle detection
void handleObstacleDetection() {
  // Check if enough time has passed since the last obstacle detection
  if (millis() - lastObstacleDetectionTime < 1000) {
    return;  // Debounce obstacle detection
  }
  
  // Update last detection time
  lastObstacleDetectionTime = millis();
  
  Serial.println("Obstacle detected!");
  
  // Stop the vehicle
  setMotorPWM(0, 0);
  delay(200);  // Short pause
  
  if (obstacleAvoidanceMode) {
    // Full obstacle avoidance sequence
    performObstacleAvoidance();
  } else {
    // Just back up and stop for normal driving modes
    setMotorPWM(-200, -200);  // Back up
    delay(1000);
    setMotorPWM(0, 0);  // Stop
  }
}

// Perform full obstacle avoidance sequence
void performObstacleAvoidance() {
  // Back up for half a second
  setMotorPWM(-200, -200);
  delay(500);
  setMotorPWM(0, 0);
  delay(200);
  
  // Scan left
  servoMotor.write(SERVO_LEFT);
  delay(500);
  float leftDistance = getDistanceCm();
  
  // Return to center
  servoMotor.write(SERVO_CENTER);
  delay(500);
  
  // Scan right
  servoMotor.write(SERVO_RIGHT);
  delay(500);
  float rightDistance = getDistanceCm();
  
  // Return to center
  servoMotor.write(SERVO_CENTER);
  delay(500);
  
  // Compare distances and choose direction
  if (leftDistance > rightDistance && leftDistance > OBSTACLE_THRESHOLD_CM) {
    // More space on the left, turn left
    Serial.println("Turning left");
    setMotorPWM(-200, 200);
    delay(500);
  } else if (rightDistance > OBSTACLE_THRESHOLD_CM) {
    // More space on the right, turn right
    Serial.println("Turning right");
    setMotorPWM(200, -200);
    delay(500);
  } else {
    // Both sides are blocked, try turning around
    Serial.println("Both sides blocked, turning around");
    setMotorPWM(255, -255);
    delay(1000);
  }
  
  // Resume forward movement in obstacle avoidance mode
  if (obstacleAvoidanceMode) {
    setMotorPWM(255, 255);
  } else {
    setMotorPWM(0, 0);  // Stop if not in obstacle avoidance mode
  }
} 