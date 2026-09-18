#include <Wire.h>
#include <Adafruit_MLX90614.h>
#include <esp_system.h>
// ESP32 SDA=21 SCL=22; JDY-31 RX=17 TX=16, 9600 8N1.
Adafruit_MLX90614 mlx;
HardwareSerial bluetooth(2);
uint32_t bootId, sequence = 0, nextSample = 0;
bool sensorReady = false;
void setup() {
  Serial.begin(115200); Wire.begin(21, 22); Wire.setTimeOut(100);
  bluetooth.begin(9600, SERIAL_8N1, 16, 17);
  bootId = esp_random(); sensorReady = mlx.begin();
}
void loop() {
  const uint32_t now = millis();
  if ((int32_t)(now - nextSample) < 0) { delay(1); return; }
  nextSample = now + 500;
  if (!sensorReady) sensorReady = mlx.begin();
  const float object = sensorReady ? mlx.readObjectTempC() : NAN;
  const float ambient = sensorReady ? mlx.readAmbientTempC() : NAN;
  const bool valid = isfinite(object) && object >= -70 && object <= 380;
  const bool ambientValid = isfinite(ambient) && ambient >= -40 && ambient <= 125;
  if (!valid) sensorReady = false;
  char rawText[16] = "", ambientText[16] = "", frame[110];
  if (valid) snprintf(rawText, sizeof(rawText), "%.2f", object);
  if (ambientValid) snprintf(ambientText, sizeof(ambientText), "%.2f", ambient);
  snprintf(frame, sizeof(frame), "CX2,%08lx,%lu,%lu,%s,%s,%d\n",
    (unsigned long)bootId, (unsigned long)sequence++, (unsigned long)now,
    rawText, ambientText, valid ? 1 : 0);
  bluetooth.print(frame); // Raw readings; app owns temporal filtering.
  Serial.print(frame);   // No actuator or actuation command.
}
