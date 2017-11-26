#include "Arduino.h"
#include "NewPing.h"

// HC-SR04 Pins
#define SON_TRIGGER_PIN 7
#define SON_ECHO_PIN 8
#define MAX_DISTANCE 200

// MOTOR_{A,B}{E,F,B} is the format
// where {A,B} represents the motor name
// and {Enable,Forward,Backwards} are the pins to that motor on the H-Bridge
#define MOTOR_AE 2
#define MOTOR_AF 10
#define MOTOR_AB 11
#define MOTOR_BE 3
#define MOTOR_BF 5
#define MOTOR_BB 6


NewPing sonar(SON_TRIGGER_PIN, SON_ECHO_PIN, MAX_DISTANCE);

void setup()
{
  // Motors are always enabled
  pinMode(MOTOR_AE, OUTPUT);
  pinMode(MOTOR_BE, OUTPUT);
  digitalWrite(MOTOR_AE, HIGH);
  digitalWrite(MOTOR_BE, HIGH);

  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(MOTOR_AF, OUTPUT);
  pinMode(MOTOR_AB, OUTPUT);
  pinMode(MOTOR_BF, OUTPUT);
  pinMode(MOTOR_BB, OUTPUT);

  Serial.begin(9600);

  // Keep the light on until connected
  digitalWrite(LED_BUILTIN, HIGH);
  while (!Serial) {}
  digitalWrite(LED_BUILTIN, LOW);

}

// reset the board state; similar to rebooting the Arduino but without
// disturbing the serial connection
void reset()
{
  digitalWrite(LED_BUILTIN, LOW);
  digitalWrite(MOTOR_AF, LOW);
  digitalWrite(MOTOR_AB, LOW);
  digitalWrite(MOTOR_BF, LOW);
  digitalWrite(MOTOR_BB, LOW);
}

// waits until a byte is available, and returns that value
int waitForByte()
{
  while (!Serial.available()) {}
  return Serial.read();
}

void loop()
{
  if (Serial.available()) {
    // read the command character from serial
    char c = Serial.read();
    if (c == 'R') {
      reset();
    }

    // commands to toggle LED
    if (c == 'L') {
      digitalWrite(LED_BUILTIN, HIGH);
    }
    if (c == 'l') {
      digitalWrite(LED_BUILTIN, LOW);
    }

    // commands to control motor A with PWM
    if (c == 'A') {
      // forwards
      analogWrite(MOTOR_AF, waitForByte());
      digitalWrite(MOTOR_AB, LOW);
    }
    if (c == 'a') {
      // stopped
      digitalWrite(MOTOR_AF, LOW);
      digitalWrite(MOTOR_AB, LOW);
    }
    if (c == 'z') {
      // backwards
      digitalWrite(MOTOR_AF, LOW);
      digitalWrite(MOTOR_AF, LOW);
      analogWrite(MOTOR_AB, waitForByte());
    }

    // commands to control motor B with PWM
    if (c == 'B') {
      // forwards
      analogWrite(MOTOR_BF, waitForByte());
      digitalWrite(MOTOR_BB, LOW);
    }
    if (c == 'b') {
      // stopped
      digitalWrite(MOTOR_BF, LOW);
      digitalWrite(MOTOR_BB, LOW);
    }
    if (c == 'y') {
      // backwards
      digitalWrite(MOTOR_BF, LOW);
      analogWrite(MOTOR_BB, waitForByte());
    }
  }

  int dist = sonar.ping_cm();
  if (dist == 0) {
    // Zeros are meaningless; they are errors from NewPing => disregard them
  } else {
    Serial.write(dist);
  }

  // include a delay to avoid overwhelming the serial bandwidth; which
  // could cause unresponsiveness
  delay(50);
}
