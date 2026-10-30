/*
  ARCANE ARENA - Arduino Uno game controller
  ------------------------------------------
  Works for BOTH versions of the game at the same time:
    - Mobile (Android app)  -> through the HC-05 Bluetooth module (SoftwareSerial, 9600 baud)
    - PC (Chrome / Edge)    -> through the USB cable (hardware Serial, 115200 baud)

  10 push buttons, each wired between the pin and GND (internal pull-ups, no resistors needed):

    D2  = UP          D7  = SKILL 1
    D3  = DOWN        D8  = SKILL 2
    D4  = LEFT        D9  = DASH
    D5  = RIGHT       A0  = SWAP HERO
    D6  = ATTACK/OK   A1  = PAUSE

  HC-05:
    VCC -> 5V      GND -> GND
    TXD -> D10 (Arduino RX)
    RXD <- D11 through a voltage divider (1k from D11 to RXD, 2k from RXD to GND)
    (HC-05 RX pin is 3.3 V logic)

  Protocol sent to the game: "S:XXX\n"  (hex bitmask of pressed buttons)
    bit 0 UP, 1 DOWN, 2 LEFT, 3 RIGHT, 4 ATTACK, 5 SKILL1, 6 SKILL2, 7 DASH, 8 SWAP, 9 PAUSE
  Sent immediately when a button changes, and every 100 ms as a heartbeat.

  The HC-05 is on pins 10/11 (not 0/1), so you can upload this sketch
  without unplugging the Bluetooth module.
*/

#include <SoftwareSerial.h>

SoftwareSerial bt(10, 11);   // RX, TX  (HC-05 TXD -> 10, HC-05 RXD <- 11 via divider)

const uint8_t NUM_BTN = 10;
const uint8_t PINS[NUM_BTN] = { 2, 3, 4, 5, 6, 7, 8, 9, A0, A1 };
const char*   NAMES[NUM_BTN] = { "UP", "DOWN", "LEFT", "RIGHT", "ATTACK", "SKILL1", "SKILL2", "DASH", "SWAP", "PAUSE" };

const unsigned long DEBOUNCE_MS  = 15;
const unsigned long HEARTBEAT_MS = 100;
const uint8_t LED = 13;

uint16_t stableState = 0;          // debounced state (1 = pressed)
uint16_t lastRaw = 0;
unsigned long lastChange[NUM_BTN];
unsigned long lastSend = 0;

void sendState() {
  char msg[8];
  snprintf(msg, sizeof(msg), "S:%03X\n", stableState);
  Serial.print(msg);   // USB -> PC version
  bt.print(msg);       // Bluetooth -> mobile version
  lastSend = millis();
}

void setup() {
  for (uint8_t i = 0; i < NUM_BTN; i++) {
    pinMode(PINS[i], INPUT_PULLUP);
    lastChange[i] = 0;
  }
  pinMode(LED, OUTPUT);
  Serial.begin(115200);
  bt.begin(9600);            // HC-05 default data-mode baud rate
  delay(200);
  Serial.println(F("# ArcaneArenaPad ready"));
}

void loop() {
  unsigned long now = millis();
  bool changed = false;

  for (uint8_t i = 0; i < NUM_BTN; i++) {
    bool pressed = (digitalRead(PINS[i]) == LOW);   // pull-up: LOW = pressed
    bool rawBit = (lastRaw >> i) & 1;
    if (pressed != rawBit) {                        // raw value moved -> restart debounce timer
      lastChange[i] = now;
      if (pressed) lastRaw |= (1 << i); else lastRaw &= ~(1 << i);
    }
    bool stableBit = (stableState >> i) & 1;
    if (pressed != stableBit && (now - lastChange[i]) >= DEBOUNCE_MS) {
      if (pressed) stableState |= (1 << i); else stableState &= ~(1 << i);
      changed = true;
    }
  }

  digitalWrite(LED, stableState ? HIGH : LOW);      // on-board LED lights while any button is held

  if (changed || (now - lastSend) >= HEARTBEAT_MS) sendState();
}
