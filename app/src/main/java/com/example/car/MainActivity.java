package com.example.car;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Locale;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import java.util.LinkedList;
import android.view.animation.Animation;
import android.graphics.drawable.AnimationDrawable;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private ToggleButton toggleJoystick, toggleGyro, toggleVoice, toggleObstacleAvoidance;
    private Button buttonConnect, buttonReset, buttonReturnHome;
    private ImageButton buttonVoice;
    private ImageView joystickKnob;
    private View gyroDot;
    private View joystickLayout;
    private TextView connectionStatusText;

    private Socket socket;
    private OutputStream outputStream;
    private boolean isConnected = false;

    private final String ESP_IP = "192.168.4.1";
    private final int ESP_PORT = 80;

    // Command history for return-to-home functionality
    private LinkedList<CommandHistoryEntry> commandHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 10000; // Larger history size as per requirements
    private static final int REQUEST_CODE_VOICE = 123;

    private float centerX, centerY, radius;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private Handler handler = new Handler();
    private Runnable discoRunnable;
    private boolean discoMode = false;
    private boolean obstacleAvoidanceMode = false;
    
    // To store command with timestamp
    private static class CommandHistoryEntry {
        String command;
        long timestamp;
        
        CommandHistoryEntry(String command, long timestamp) {
            this.command = command;
            this.timestamp = timestamp;
        }
    }
    
    // Last command timestamp for timing controls
    private long lastCommandTimestamp = 0;
    
    // Constants for obstacle avoidance
    private static final String COMMAND_OBSTACLE_ON = "OBSTACLE_ON";
    private static final String COMMAND_OBSTACLE_OFF = "OBSTACLE_OFF";
    private static final float OBSTACLE_THRESHOLD_CM = 20.0f;
    
    // Multiple voice command keywords
    private static final String[] FORWARD_COMMANDS = {"forward", "go forward", "move forward", "go ahead", "straight", "આગળ","હાલતો થા","હાલ તોથા","હાલ તો થા"};
    private static final String[] BACKWARD_COMMANDS = {"backward", "go backward", "move backward", "back", "reverse", "પાછળ","પાછો ચાલ","પછીનો જા","પાછી નો જા", "પાછી નોજા"};
    private static final String[] LEFT_COMMANDS = {"left", "turn left", "go left", "ડાબી", "ડાબી બાજું","ડાબી બાજુ","ડાબી બાજુ વાળ","ડાબી બાજું વાળ", "ડાબી બાજું વળ", "ડાબી બાજુ વળ"};
    private static final String[] RIGHT_COMMANDS = {"right", "turn right", "go right", "જમણી બાજું", "જમણી બાજુ", "જમણી","જમણી બાજું વાળ","જમણી બાજું વળ","જમણી બાજુ વાળ","જમણી બાજુ વાળ", };
    private static final String[] STOP_COMMANDS = {"stop", "halt", "brake", "freeze", "ઉભો રે", "ઊભો રે",};
    private static final String[] CIRCLE_COMMANDS = {"circle", "spin", "rotate", "turn around"};
    private static final String[] DISCO_COMMANDS = {"disco", "disco mode", "party", "dance"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // Set immersive fullscreen mode
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            
        setContentView(R.layout.activity_main);

        initializeViews();
        setupListeners();
        setupAnimations();
        
        // Set initial joystick knob position in post
        joystickLayout.post(() -> {
            centerJoystickKnob();
        });
        
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
         accelerometer = sensorManager != null ?
                        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
                        
        requestPermissionsIfNeeded();
        
        // Set initial control visibility
        // If a control is already toggled on, show only that control
        if (toggleJoystick.isChecked()) {
            showJoystickOnly();
        } else if (toggleGyro.isChecked()) {
            showGyroOnly();
        } else {
            // Default state: show both controls
            showBothControls();
        }
    }

    private void initializeViews() {
        toggleJoystick = findViewById(R.id.toggleJoystick);
        toggleGyro = findViewById(R.id.toggleGyro);
        toggleVoice = findViewById(R.id.toggleVoice);
        toggleObstacleAvoidance = findViewById(R.id.toggleObstacleAvoidance);
        buttonConnect = findViewById(R.id.buttonConnect);
        buttonReset = findViewById(R.id.buttonReset);
        buttonReturnHome = findViewById(R.id.buttonReturnHome);
        buttonVoice = findViewById(R.id.buttonVoice);
        joystickKnob = findViewById(R.id.joystickKnob);
        gyroDot = findViewById(R.id.gyroDot);
        joystickLayout = findViewById(R.id.joystickLayout);
        connectionStatusText = findViewById(R.id.textConnectionStatus);
        
        updateConnectionStatus(false);
    }

    private void setupListeners() {
        buttonConnect.setOnClickListener(v -> {
            if (isConnected) {
                closeConnection();
                Toast.makeText(this, "Disconnected from ESP8266", Toast.LENGTH_SHORT).show();
            } else {
                connectToESP();
            }
        });
        
        // Mutual exclusivity for toggle buttons
        toggleJoystick.setOnCheckedChangeListener((b, on) -> {
            if (on) {
                // Turn off other control toggles
                toggleGyro.setChecked(false);
                toggleVoice.setChecked(false);
                toggleObstacleAvoidance.setChecked(false);
                discoMode = false;
                if (handler != null) {
                    handler.removeCallbacks(discoRunnable);
                }
                // Stop the car when switching modes
                sendPWM(0, 0);
                // Show joystick, hide gyro
                showJoystickOnly();
            } else if (!toggleGyro.isChecked() && !toggleVoice.isChecked() && !toggleObstacleAvoidance.isChecked()) {
                // If no control is active, show both
                showBothControls();
                // Stop the car when no mode is active
                sendPWM(0, 0);
            }
        });
        
        toggleGyro.setOnCheckedChangeListener((b, on) -> {
            if (on) {
                // Turn off other control toggles
                toggleJoystick.setChecked(false);
                toggleVoice.setChecked(false);
                toggleObstacleAvoidance.setChecked(false);
                discoMode = false;
                if (handler != null) {
                    handler.removeCallbacks(discoRunnable);
                }
                registerSensorListener();
                // Stop the car when switching modes
                sendPWM(0, 0);
                // Show gyro, hide joystick
                showGyroOnly();
            } else {
                unregisterSensorListener();
                // If no control is active, show both
                if (!toggleJoystick.isChecked() && !toggleVoice.isChecked() && !toggleObstacleAvoidance.isChecked()) {
                    showBothControls();
                    // Stop the car when no mode is active
                    sendPWM(0, 0);
                }
            }
        });
        
        toggleVoice.setOnCheckedChangeListener((b, on) -> {
            buttonVoice.setEnabled(on);
            if (on) {
                // Turn off other control toggles
                toggleJoystick.setChecked(false);
                toggleGyro.setChecked(false);
                toggleObstacleAvoidance.setChecked(false);
                discoMode = false;
                if (handler != null) {
                    handler.removeCallbacks(discoRunnable);
                }
                // Stop the car when switching modes
                sendPWM(0, 0);
            } else {
                // Stop the car when voice command mode is turned off
                sendPWM(0, 0);
            }
        });
        
        toggleObstacleAvoidance.setOnCheckedChangeListener((b, on) -> {
            obstacleAvoidanceMode = on;
            if (on) {
                // Turn off other control toggles
                toggleJoystick.setChecked(false);
                toggleGyro.setChecked(false);
                toggleVoice.setChecked(false);
                discoMode = false;
                if (handler != null) {
                    handler.removeCallbacks(discoRunnable);
                }
                // Send command to ESP to activate obstacle avoidance mode
                sendCommand(COMMAND_OBSTACLE_ON + "\n");
                Toast.makeText(this, "Obstacle Avoidance Mode Active", Toast.LENGTH_SHORT).show();
            } else {
                // Send command to ESP to deactivate obstacle avoidance mode
                sendCommand(COMMAND_OBSTACLE_OFF + "\n");
                // Stop the car when obstacle avoidance is turned off
                sendPWM(0, 0);
                Toast.makeText(this, "Obstacle Avoidance Mode Deactivated", Toast.LENGTH_SHORT).show();
            }
        });


        joystickKnob.setOnTouchListener((v, event) -> {
            if (!toggleJoystick.isChecked()) return false;
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // Get the joystick layout dimensions
                    int joystickWidth = joystickLayout.getWidth();
                    int joystickHeight = joystickLayout.getHeight();
                    
                    // Calculate the center of the joystick area
                    float centerX = joystickWidth / 2f;
                    float centerY = joystickHeight / 2f;
                    
                    // Get touch location relative to the view's parent
                    float touchX = event.getX() + joystickKnob.getX();
                    float touchY = event.getY() + joystickKnob.getY();
                    
                    // Calculate the offset from center
                    float offsetX = touchX - centerX;
                    float offsetY = touchY - centerY;
                    
                    // Calculate the maximum visible radius (for control calculation)
                    // This will allow the knob to be half visible outside
                    float knobRadius = joystickKnob.getWidth() / 2f;
                    float joystickRadius = Math.min(joystickWidth, joystickHeight) / 2f;
                    float maxVisualRadius = joystickRadius; // Allow to move up to edge of joystick
                    float maxControlRadius = joystickRadius - (knobRadius / 2); // For control calculations
                    
                    // Calculate the distance from center
                    float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY);
                    
                    // Store original offsets for visual position
                    float visualOffsetX = offsetX;
                    float visualOffsetY = offsetY;
                    
                    // Allow the knob to be partially outside the boundary visually
                    if (distance > maxVisualRadius) {
                        visualOffsetX = offsetX * maxVisualRadius / distance;
                        visualOffsetY = offsetY * maxVisualRadius / distance;
                    }
                    
                    // For control values, calculate normalized positions within control radius
                    float controlDistance = Math.min(distance, maxControlRadius);
                    float controlOffsetX = offsetX;
                    float controlOffsetY = offsetY;
                    
                    if (distance > maxControlRadius) {
                        controlOffsetX = offsetX * maxControlRadius / distance;
                        controlOffsetY = offsetY * maxControlRadius / distance;
                    }
                    
                    // Calculate normalized values for controls (-1 to 1)
                    float relX = controlOffsetX / maxControlRadius;
                    float relY = controlOffsetY / maxControlRadius;
                    
                    // Invert Y for forward/backward control
                    float controlRelY = -relY;
                    
                    // Calculate angle and magnitude for control
                    double angle = Math.toDegrees(Math.atan2(controlRelY, relX));
                    if (angle < 0) angle += 360;
                    
                    float magnitude = Math.min(1.0f, controlDistance / maxControlRadius);
                    
                    // Determine PWM values based on the mathematical formula provided
                    int leftPWM = 0, rightPWM = 0;
                    
                    // Apply the zoned PWM calculation based on angle
                    if (angle >= 0 && angle < 45) {
                        // Zone 1
                        leftPWM = (int) (255 * magnitude);
                        rightPWM = (int) ((-255 + (angle / 45) * 255) * magnitude);
                    } else if (angle >= 45 && angle < 90) {
                        // Zone 2
                        leftPWM = (int) (255 * magnitude);
                        rightPWM = (int) (((angle - 45) / 45 * 255) * magnitude);
                    } else if (angle >= 90 && angle < 135) {
                        // Zone 3
                        leftPWM = (int) ((255 - ((angle - 90) / 45 * 255)) * magnitude);
                        rightPWM = (int) (255 * magnitude);
                    } else if (angle >= 135 && angle < 180) {
                        // Zone 4
                        leftPWM = (int) ((-((angle - 135) / 45 * 255)) * magnitude);
                        rightPWM = (int) (255 * magnitude);
                    } else if (angle >= 180 && angle < 202.5) {
                        // Zone 5
                        leftPWM = (int) ((-255 + ((angle - 180) / 22.5 * 255)) * magnitude);
                        rightPWM = (int) ((255 - ((angle - 180) / 22.5 * 255)) * magnitude);
                    } else if (angle >= 202.5 && angle < 225) {
                        // Zone 6
                        leftPWM = (int) (((angle - 202.5) / 22.5 * 255) * magnitude);
                        rightPWM = (int) ((-((angle - 202.5) / 22.5 * 255)) * magnitude);
                    } else if (angle >= 225 && angle < 270) {
                        // Zone 7
                        leftPWM = (int) ((255 - ((angle - 225) / 45 * 510)) * magnitude);
                        rightPWM = (int) (-255 * magnitude);
                    } else if (angle >= 270 && angle < 315) {
                        // Zone 8
                        leftPWM = (int) (-255 * magnitude);
                        rightPWM = (int) ((-255 + ((angle - 270) / 45 * 510)) * magnitude);
                    } else if (angle >= 315 && angle < 337.5) {
                        // Zone 9
                        leftPWM = (int) ((-255 + ((angle - 315) / 22.5 * 255)) * magnitude);
                        rightPWM = (int) ((255 - ((angle - 315) / 22.5 * 255)) * magnitude);
                    } else if (angle >= 337.5 && angle <= 360) {
                        // Zone 10
                        leftPWM = (int) (((angle - 337.5) / 22.5 * 255) * magnitude);
                        rightPWM = (int) ((-((angle - 337.5) / 22.5 * 255)) * magnitude);
                    }

                    // Position the knob relative to the center - using visual offsets
                    // This allows the knob to go partially outside the circle
                    float knobX = centerX - joystickKnob.getWidth() / 2f + visualOffsetX;
                    float knobY = centerY - joystickKnob.getHeight() / 2f + visualOffsetY;
                    
                    // Set the knob position
                    joystickKnob.setX(knobX);
                    joystickKnob.setY(knobY);

                    // Send PWM values to ESP8266
                    sendPWM(leftPWM, rightPWM);
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Reset the joystick knob position
                    centerJoystickKnob();
                    // Stop the car when user releases joystick
                    sendPWM(0, 0);
                    return true;
            }
            return false;
        });

        buttonReset.setOnClickListener(v -> {
            // Clear command history
            commandHistory.clear();
            // Stop all active modes and functions
            toggleJoystick.setChecked(false);
            toggleGyro.setChecked(false);
            toggleVoice.setChecked(false);
            toggleObstacleAvoidance.setChecked(false);
            discoMode = false;
            obstacleAvoidanceMode = false;
            
            if (handler != null) {
                handler.removeCallbacks(discoRunnable);
            }
            
            // Stop the car
            sendPWM(0, 0);
            
            // Send command to ESP to deactivate obstacle avoidance mode
            sendCommand(COMMAND_OBSTACLE_OFF + "\n");
            
            Toast.makeText(this, "All functions reset", Toast.LENGTH_SHORT).show();
        });

        buttonReturnHome.setOnClickListener(v -> {
            // Turn off all other modes
            toggleJoystick.setChecked(false);
            toggleGyro.setChecked(false);
            toggleVoice.setChecked(false);
            toggleObstacleAvoidance.setChecked(false);
            discoMode = false;
            
            if (handler != null) {
                handler.removeCallbacks(discoRunnable);
            }
            
            Toast.makeText(this, "Return to Home activated", Toast.LENGTH_SHORT).show();
            
            new Thread(() -> {
                // Execute return-to-home function by reversing all commands in history
                for (int i = commandHistory.size() - 1; i >= 0; i--) {
                    CommandHistoryEntry entry = commandHistory.get(i);
                    String cmd = entry.command;
                    
                    // Skip obstacle avoidance commands
                    if (cmd.startsWith(COMMAND_OBSTACLE_ON) || cmd.startsWith(COMMAND_OBSTACLE_OFF)) {
                        continue;
                    }
                    
                    // Parse the command to reverse PWM values
                    if (cmd.contains(",")) {
                        String[] parts = cmd.split(",");
                        try {
                            int leftPWM = Integer.parseInt(parts[0].trim());
                            int rightPWM = Integer.parseInt(parts[1].trim());
                            
                            // Reverse the PWM values
                            String reversedCmd = (-leftPWM) + "," + (-rightPWM) + "\n";
                            
                            // Get time delay from history
                            long delay = 0;
                            if (i > 0) {
                                delay = entry.timestamp - commandHistory.get(i-1).timestamp;
                                // Cap the delay to prevent excessive waiting
                                if (delay > 5000) delay = 5000;
                            }
                            
                            // Send the reversed command
                            sendCommand(reversedCmd);
                            
                            // Wait for the original command's duration
                            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
                        } catch (NumberFormatException e) {
                            // Skip invalid commands
                            e.printStackTrace();
                        }
                    }
                }
                
                // Stop the car after return-to-home is complete
                sendPWM(0, 0);
                
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Return to Home completed", Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        buttonVoice.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            } else {
                startVoiceRecognition();
            }
        });
    }

    private void registerSensorListener() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME);
        } else if (accelerometer == null) {
            Toast.makeText(this, "Accelerometer not available on this device", 
                           Toast.LENGTH_SHORT).show();
            toggleGyro.setChecked(false);
        }
    }

    private void unregisterSensorListener() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void sendPWM(int left, int right) {
        left = Math.max(-255, Math.min(255, left));
        right = Math.max(-255, Math.min(255, right));
        String command = left + "," + right + "\n";
        sendCommand(command);
        addToCommandHistory(command);
    }
    
    private void addToCommandHistory(String command) {
        long currentTime = System.currentTimeMillis();
        commandHistory.add(new CommandHistoryEntry(command, currentTime));
        lastCommandTimestamp = currentTime;
        
        // Prevent memory issues by limiting history size
        if (commandHistory.size() > MAX_HISTORY_SIZE) {
            commandHistory.removeFirst();
        }
    }

    private void sendCommand(String command) {
        if (!isConnected) {
            Toast.makeText(this, "Not connected to ESP8266", Toast.LENGTH_SHORT).show();
            updateConnectionStatus(false);
            return;
        }
        
        new Thread(() -> {
            try {
                if (socket != null && outputStream != null && !socket.isClosed()) {
                    outputStream.write(command.getBytes());
                    outputStream.flush();
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Connection lost", Toast.LENGTH_SHORT).show();
                        isConnected = false;
                        updateConnectionStatus(false);
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show();
                    isConnected = false;
                    updateConnectionStatus(false);
                });
                closeConnection();
            }
        }).start();
    }

    private void connectToESP() {
        runOnUiThread(() -> {
            buttonConnect.setEnabled(false);
            buttonConnect.setText("Connecting...");
            updateConnectionStatus(false);
        });
        
        new Thread(() -> {
            closeConnection(); // Close any existing connection first
            
            try {
                socket = new Socket(ESP_IP, ESP_PORT);
                outputStream = socket.getOutputStream();
                isConnected = true;
                runOnUiThread(() -> {
                    Toast.makeText(this, "Connected to ESP8266", Toast.LENGTH_SHORT).show();
                    buttonConnect.setEnabled(true);
                    buttonConnect.setText("Disconnect");
                    updateConnectionStatus(true);
                });
                
                // Initialize obstacle avoidance state
                if (toggleObstacleAvoidance.isChecked()) {
                    sendCommand(COMMAND_OBSTACLE_ON + "\n");
                } else {
                    sendCommand(COMMAND_OBSTACLE_OFF + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
                    buttonConnect.setEnabled(true);
                    buttonConnect.setText("Connect");
                    isConnected = false;
                    updateConnectionStatus(false);
                });
            }
        }).start();
    }
    
    private void updateConnectionStatus(boolean connected) {
        if (connectionStatusText != null) {
            String statusText = "Status: " + (connected ? "Connected" : "Disconnected");
            connectionStatusText.setText(statusText);
            connectionStatusText.setTextColor(getResources().getColor(
                connected ? android.R.color.holo_green_light : android.R.color.holo_red_light
            ));
        }
        
        if (buttonConnect != null) {
            buttonConnect.setText(connected ? "Disconnect" : "Connect");
        }
    }
    
    private void closeConnection() {
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (socket != null) {
                socket.close();
                socket = null;
            }
            isConnected = false;
            runOnUiThread(() -> updateConnectionStatus(false));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak command");
        try {
            startActivityForResult(intent, REQUEST_CODE_VOICE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported on this device", 
                          Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String voiceCommand = results.get(0).toLowerCase();
                handleVoiceCommand(voiceCommand);
            }
        }
    }

    private void handleVoiceCommand(String command) {
        if (!toggleVoice.isChecked()) {
            return;
        }
        
        // Handle multiple command variations
        if (matchesAnyCommand(command, FORWARD_COMMANDS)) {
            // Forward movement until new command is given
            sendPWM(255, 255);
            Toast.makeText(this, "Moving forward", Toast.LENGTH_SHORT).show();
        } 
        else if (matchesAnyCommand(command, BACKWARD_COMMANDS)) {
            // Backward movement for 2 seconds
            sendPWM(-255, -255);
            Toast.makeText(this, "Moving backward for 2 seconds", Toast.LENGTH_SHORT).show();
            
            // After 2 seconds, stop
            handler.postDelayed(() -> {
                if (toggleVoice.isChecked()) {
                    sendPWM(0, 0);
                }
            }, 2000);
        } 
        else if (matchesAnyCommand(command, LEFT_COMMANDS)) {
            // Left turn for 1/3 second
            sendPWM(-255, 255);
            Toast.makeText(this, "Turning left", Toast.LENGTH_SHORT).show();
            
            // After 1/3 second, stop
            handler.postDelayed(() -> {
                if (toggleVoice.isChecked()) {
                    sendPWM(0, 0);
                }
            }, 500);
        } 
        else if (matchesAnyCommand(command, RIGHT_COMMANDS)) {
            // Right turn for 1/3 second
            sendPWM(255, -255);
            Toast.makeText(this, "Turning right", Toast.LENGTH_SHORT).show();
            
            // After 1/3 second, stop
            handler.postDelayed(() -> {
                if (toggleVoice.isChecked()) {
                    sendPWM(0, 0);
                }
            }, 500);
        } 
        else if (matchesAnyCommand(command, CIRCLE_COMMANDS)) {
            // Circle mode for 5 seconds
            sendPWM(250, -250);
            Toast.makeText(this, "Circle mode activated for 5 seconds", Toast.LENGTH_SHORT).show();
            
            // After 5 seconds, stop
            handler.postDelayed(() -> {
                if (toggleVoice.isChecked()) {
                    sendPWM(0, 0);
                }
            }, 5000);
        } 
        else if (matchesAnyCommand(command, DISCO_COMMANDS)) {
            if (!discoMode) {
                discoMode = true;
                startDisco();
                Toast.makeText(this, "Disco mode activated", Toast.LENGTH_SHORT).show();
            } else {
                discoMode = false;
                if (handler != null) {
                    handler.removeCallbacks(discoRunnable);
                }
                sendPWM(0, 0);
                Toast.makeText(this, "Disco mode deactivated", Toast.LENGTH_SHORT).show();
            }
        } 
        else if (matchesAnyCommand(command, STOP_COMMANDS)) {
            // Stop the car
            sendPWM(0, 0);
            discoMode = false;
            if (handler != null) {
                handler.removeCallbacks(discoRunnable);
            }
            Toast.makeText(this, "Stopping", Toast.LENGTH_SHORT).show();
        } 
        else {
            Toast.makeText(this, "Unknown Command: " + command, Toast.LENGTH_SHORT).show();
        }
    }
    
    // Helper method to check if a command matches any of the keywords
    private boolean matchesAnyCommand(String input, String[] keywords) {
        for (String keyword : keywords) {
            if (input.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void startDisco() {
        discoRunnable = new Runnable() {
            boolean toggle = true;

            @Override
            public void run() {
                if (!discoMode) return;
                if (toggle) {
                    sendPWM(250, 128);
                } else {
                    sendPWM(128, 250);
                }
                toggle = !toggle;
                if (handler != null) {
                    handler.postDelayed(this, 1000);
                }
            }
        };
        if (handler != null) {
            handler.post(discoRunnable);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!toggleGyro.isChecked() || event == null) return;

        float x = event.values[0];
        float y = event.values[1];

        // Invert X for correct left/right control (tilting left should turn left)
        float relX = -x / 9.8f;
        float relY = y / 9.8f;

        relX = Math.max(-1, Math.min(1, relX));
        relY = Math.max(-1, Math.min(1, relY));
        
        // For display, keep the original orientation
        float displayRelX = -relX; // We display with non-inverted X so it matches phone tilt
        float displayRelY = relY;
        
        // Invert relY for correct forward/backward control
        // This makes tilting forward move the car forward
        relY = -relY;
        
        // Calculate angle and magnitude for accurate PWM calculation
        double angle = Math.toDegrees(Math.atan2(relY, relX));
        if (angle < 0) angle += 360;
        
        float magnitude = (float) Math.sqrt(relX * relX + relY * relY);
        if (magnitude > 1.0f) magnitude = 1.0f;
        
        // Determine PWM values based on the mathematical formula
        int leftPWM = 0, rightPWM = 0;
        
        // Apply the same zoned PWM calculation as joystick
        if (angle >= 0 && angle < 45) {
            // Zone 1
            leftPWM = (int) (255 * magnitude);
            rightPWM = (int) ((-255 + (angle / 45) * 255) * magnitude);
        } else if (angle >= 45 && angle < 90) {
            // Zone 2
            leftPWM = (int) (255 * magnitude);
            rightPWM = (int) (((angle - 45) / 45 * 255) * magnitude);
        } else if (angle >= 90 && angle < 135) {
            // Zone 3
            leftPWM = (int) ((255 - ((angle - 90) / 45 * 255)) * magnitude);
            rightPWM = (int) (255 * magnitude);
        } else if (angle >= 135 && angle < 180) {
            // Zone 4
            leftPWM = (int) ((-((angle - 135) / 45 * 255)) * magnitude);
            rightPWM = (int) (255 * magnitude);
        } else if (angle >= 180 && angle < 202.5) {
            // Zone 5
            leftPWM = (int) ((-255 + ((angle - 180) / 22.5 * 255)) * magnitude);
            rightPWM = (int) ((255 - ((angle - 180) / 22.5 * 255)) * magnitude);
        } else if (angle >= 202.5 && angle < 225) {
            // Zone 6
            leftPWM = (int) (((angle - 202.5) / 22.5 * 255) * magnitude);
            rightPWM = (int) ((-((angle - 202.5) / 22.5 * 255)) * magnitude);
        } else if (angle >= 225 && angle < 270) {
            // Zone 7
            leftPWM = (int) ((255 - ((angle - 225) / 45 * 510)) * magnitude);
            rightPWM = (int) (-255 * magnitude);
        } else if (angle >= 270 && angle < 315) {
            // Zone 8
            leftPWM = (int) (-255 * magnitude);
            rightPWM = (int) ((-255 + ((angle - 270) / 45 * 510)) * magnitude);
        } else if (angle >= 315 && angle < 337.5) {
            // Zone 9
            leftPWM = (int) ((-255 + ((angle - 315) / 22.5 * 255)) * magnitude);
            rightPWM = (int) ((255 - ((angle - 315) / 22.5 * 255)) * magnitude);
        } else if (angle >= 337.5 && angle <= 360) {
            // Zone 10
            leftPWM = (int) (((angle - 337.5) / 22.5 * 255) * magnitude);
            rightPWM = (int) ((-((angle - 337.5) / 22.5 * 255)) * magnitude);
        }

        // Move the gyro dot visually - use the original orientation values
        if (gyroDot != null && gyroDot.getParent() instanceof View) {
            View gyroContainer = (View) gyroDot.getParent();
            
            // Get container dimensions
            float containerWidth = gyroContainer.getWidth();
            float containerHeight = gyroContainer.getHeight();
            float containerRadius = Math.min(containerWidth, containerHeight) / 2f;
            
            // Calculate dot center position using the display values
            float dotX = displayRelX * containerRadius;
            float dotY = -displayRelY * containerRadius; // Keep Y-axis inverted for display
            
            // Position the dot using absolute positioning
            float centerX = containerWidth / 2f;
            float centerY = containerHeight / 2f;
            
            gyroDot.setX(centerX + dotX - gyroDot.getWidth() / 2f);
            gyroDot.setY(centerY + dotY - gyroDot.getHeight() / 2f);
        }

        // Send PWM values to ESP8266
        sendPWM(leftPWM, rightPWM);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void requestPermissionsIfNeeded() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        };
        
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, 1);
                break;
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Re-apply fullscreen immersive mode
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        
        // Ensure joystick is centered   
        joystickLayout.post(() -> {
            centerJoystickKnob();
        });
            
        if (toggleGyro.isChecked()) {
            registerSensorListener();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        unregisterSensorListener();
        
        // Stop any active modes
        if (discoMode) {
            discoMode = false;
            if (handler != null) {
                handler.removeCallbacks(discoRunnable);
            }
        }
        
        // Reset visual positions
        centerJoystickKnob();
        
        if (gyroDot != null && gyroDot.getParent() instanceof View) {
            View container = (View) gyroDot.getParent();
            float centerX = container.getWidth() / 2f - gyroDot.getWidth() / 2f;
            float centerY = container.getHeight() / 2f - gyroDot.getHeight() / 2f;
            gyroDot.setX(centerX);
            gyroDot.setY(centerY);
        }
        
        // Stop the car when app is paused
        if (isConnected) {
            sendPWM(0, 0);
            if (toggleObstacleAvoidance.isChecked()) {
                sendCommand(COMMAND_OBSTACLE_OFF + "\n");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterSensorListener();
        closeConnection();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    /**
     * Shows only the joystick control, hides gyro
     */
    private void showJoystickOnly() {
        View joystickSection = findViewById(R.id.joystickSection);
        View gyroSection = findViewById(R.id.gyroSection);
        
        if (joystickSection != null) joystickSection.setVisibility(View.VISIBLE);
        if (gyroSection != null) gyroSection.setVisibility(View.GONE);
    }
    
    /**
     * Shows only the gyro control, hides joystick
     */
    private void showGyroOnly() {
        View joystickSection = findViewById(R.id.joystickSection);
        View gyroSection = findViewById(R.id.gyroSection);
        
        if (joystickSection != null) joystickSection.setVisibility(View.GONE);
        if (gyroSection != null) gyroSection.setVisibility(View.VISIBLE);
    }
    
    /**
     * Shows both controls
     */
    private void showBothControls() {
        View joystickSection = findViewById(R.id.joystickSection);
        View gyroSection = findViewById(R.id.gyroSection);
        
        if (joystickSection != null) joystickSection.setVisibility(View.VISIBLE);
        if (gyroSection != null) gyroSection.setVisibility(View.VISIBLE);
    }

    /**
     * Setup the background animations
     */
    private void setupAnimations() {
        // Start background animation
        RelativeLayout rootLayout = findViewById(R.id.rootLayout);
        if (rootLayout != null && rootLayout.getBackground() instanceof AnimationDrawable) {
            AnimationDrawable animationDrawable = (AnimationDrawable) rootLayout.getBackground();
            animationDrawable.setEnterFadeDuration(2000);
            animationDrawable.setExitFadeDuration(4000);
            animationDrawable.start();
        }
        
        // Setup particles animation
        ImageView particlesOverlay = findViewById(R.id.particlesOverlay);
        if (particlesOverlay != null) {
            // Apply rotation animation
            Animation rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_particles);
            
            // Apply fade animation
            Animation fadeAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_particles);
            
            // Create animation set
            AnimationSet animationSet = new AnimationSet(true);
            animationSet.addAnimation(rotateAnimation);
            animationSet.addAnimation(fadeAnimation);
            
            // Start animations
            particlesOverlay.startAnimation(animationSet);
        }
    }

    // Helper method to properly center the joystick knob
    private void centerJoystickKnob() {
        if (joystickKnob != null && joystickLayout != null) {
            // Center position is the middle of the joystick layout
            float centerX = (joystickLayout.getWidth() - joystickKnob.getWidth()) / 2f;
            float centerY = (joystickLayout.getHeight() - joystickKnob.getHeight()) / 2f;
            joystickKnob.setX(centerX);
            joystickKnob.setY(centerY);
        }
    }
}
