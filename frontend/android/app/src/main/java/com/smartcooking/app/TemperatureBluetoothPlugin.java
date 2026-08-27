package com.smartcooking.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CapacitorPlugin(
    name = "TemperatureBluetooth",
    permissions = {
        @Permission(strings = { Manifest.permission.BLUETOOTH_CONNECT }, alias = "bluetoothConnect")
    }
)
public class TemperatureBluetoothPlugin extends Plugin {

    private static final String TAG = "TemperatureBluetooth";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("^TEMP:([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))$");
    private static final Pattern PLAIN_TEMPERATURE_PATTERN = Pattern.compile("^[-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)$");
    private static final double MIN_TEMPERATURE = -50.0;
    private static final double MAX_TEMPERATURE = 500.0;
    private static final int MAX_BUFFER_LENGTH = 4096;

    private final ExecutorService bluetoothExecutor = Executors.newSingleThreadExecutor();
    private final Object connectionLock = new Object();
    private volatile BluetoothSocket socket;
    private volatile InputStream inputStream;
    private volatile Future<?> connectionTask;
    private volatile boolean connecting;
    private volatile boolean connected;

    @PluginMethod
    public void connect(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getPermissionState("bluetoothConnect") != PermissionState.GRANTED) {
            requestPermissionForAlias("bluetoothConnect", call, "bluetoothPermissionCallback");
            return;
        }

        startConnection(call);
    }

    @PermissionCallback
    public void bluetoothPermissionCallback(PluginCall call) {
        if (getPermissionState("bluetoothConnect") != PermissionState.GRANTED) {
            call.reject("Bluetooth connect permission was denied", "PERMISSION_DENIED");
            return;
        }

        startConnection(call);
    }

    private void startConnection(PluginCall call) {
        String address = call.getString("address");
        if (address == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            call.reject("A valid paired Bluetooth MAC address is required", "INVALID_ADDRESS");
            return;
        }

        synchronized (connectionLock) {
            if (connected) {
                JSObject result = new JSObject();
                result.put("status", "connected");
                result.put("address", address);
                call.resolve(result);
                return;
            }
            if (connecting) {
                call.reject("A Bluetooth connection attempt is already in progress", "CONNECTION_IN_PROGRESS");
                return;
            }
            connecting = true;
        }

        connectionTask = bluetoothExecutor.submit(() -> connectAndRead(address, call));
    }

    private void connectAndRead(String address, PluginCall call) {
        try {
            BluetoothManager manager = (BluetoothManager) getContext().getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) {
                throw new BluetoothConnectionException("Bluetooth is not supported on this device", "BLUETOOTH_UNAVAILABLE");
            }
            if (!adapter.isEnabled()) {
                throw new BluetoothConnectionException("Bluetooth is disabled", "BLUETOOTH_DISABLED");
            }

            BluetoothDevice device = findPairedDevice(adapter.getBondedDevices(), address);
            if (device == null) {
                throw new BluetoothConnectionException("The Bluetooth device is not paired in Android settings", "DEVICE_NOT_PAIRED");
            }

            String connectionMode = "secure";
            try {
                connectSocket(device, true);
            } catch (IOException secureError) {
                Log.w(TAG, "Secure RFCOMM connection failed; trying insecure RFCOMM fallback", secureError);
                closeResources();
                if (Thread.currentThread().isInterrupted()) {
                    throw secureError;
                }
                connectionMode = "insecure";
                connectSocket(device, false);
            }

            synchronized (connectionLock) {
                connected = true;
                connecting = false;
            }

            JSObject result = new JSObject();
            result.put("status", "connected");
            result.put("address", address);
            result.put("mode", connectionMode);
            call.resolve(result);

            try {
                readTemperatureStream();
            } catch (IOException readError) {
                Log.w(TAG, "Bluetooth temperature stream closed after a read error", readError);
            }
        } catch (BluetoothConnectionException error) {
            call.reject(error.getMessage(), error.code, error);
        } catch (SecurityException error) {
            call.reject("Bluetooth permission is missing", "PERMISSION_DENIED", error);
        } catch (IOException error) {
            call.reject("Unable to connect to the paired Bluetooth device", "CONNECTION_FAILED", error);
        } catch (Exception error) {
            call.reject("Unexpected Bluetooth connection error", "BLUETOOTH_ERROR", error);
        } finally {
            synchronized (connectionLock) {
                connecting = false;
                connected = false;
            }
            closeResources();
        }
    }

    private BluetoothDevice findPairedDevice(Set<BluetoothDevice> pairedDevices, String address) {
        for (BluetoothDevice device : pairedDevices) {
            if (address.equalsIgnoreCase(device.getAddress())) {
                return device;
            }
        }
        return null;
    }

    private void connectSocket(BluetoothDevice device, boolean secure) throws IOException {
        BluetoothSocket candidate = secure
            ? device.createRfcommSocketToServiceRecord(SPP_UUID)
            : device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
        socket = candidate;
        candidate.connect();
        inputStream = candidate.getInputStream();
    }

    private void readTemperatureStream() throws IOException {
        byte[] chunk = new byte[256];
        StringBuilder buffer = new StringBuilder();

        while (connected && !Thread.currentThread().isInterrupted()) {
            InputStream currentInput = inputStream;
            if (currentInput == null) {
                break;
            }

            int bytesRead = currentInput.read(chunk);
            if (bytesRead < 0) {
                break;
            }

            buffer.append(new String(chunk, 0, bytesRead, StandardCharsets.US_ASCII));
            int newlineIndex;
            while ((newlineIndex = buffer.indexOf("\n")) >= 0) {
                String line = buffer.substring(0, newlineIndex);
                buffer.delete(0, newlineIndex + 1);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                processTemperatureLine(line.trim());
            }

            if (buffer.length() > MAX_BUFFER_LENGTH) {
                Log.w(TAG, "Discarding oversized unterminated Bluetooth input");
                buffer.setLength(0);
            }
        }
    }

    private void processTemperatureLine(String line) {
        Matcher matcher = TEMPERATURE_PATTERN.matcher(line);
        String temperatureValue;
        if (matcher.matches()) {
            temperatureValue = matcher.group(1);
        } else if (PLAIN_TEMPERATURE_PATTERN.matcher(line).matches()) {
            temperatureValue = line;
        } else {
            return;
        }

        try {
            double temperature = Double.parseDouble(temperatureValue);
            if (!Double.isFinite(temperature) || temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
                return;
            }

            JSObject data = new JSObject();
            data.put("temperature", temperature);
            data.put("raw", line);
            data.put("timestamp", System.currentTimeMillis());
            notifyListeners("temperatureData", data);
        } catch (NumberFormatException ignored) {
            // Ignore malformed serial data.
        }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        disconnectInternal();
        JSObject result = new JSObject();
        result.put("status", "disconnected");
        call.resolve(result);
    }

    @PluginMethod
    public void isConnected(PluginCall call) {
        JSObject result = new JSObject();
        result.put("connected", connected && socket != null && socket.isConnected());
        call.resolve(result);
    }

    private void disconnectInternal() {
        synchronized (connectionLock) {
            connecting = false;
            connected = false;
            Future<?> currentTask = connectionTask;
            if (currentTask != null) {
                currentTask.cancel(true);
                connectionTask = null;
            }
            closeResources();
        }
    }

    private void closeResources() {
        InputStream currentInput = inputStream;
        inputStream = null;
        if (currentInput != null) {
            try {
                currentInput.close();
            } catch (IOException error) {
                Log.w(TAG, "Failed to close Bluetooth input stream", error);
            }
        }

        BluetoothSocket currentSocket = socket;
        socket = null;
        if (currentSocket != null) {
            try {
                currentSocket.close();
            } catch (IOException error) {
                Log.w(TAG, "Failed to close Bluetooth socket", error);
            }
        }
    }

    @Override
    protected void handleOnDestroy() {
        disconnectInternal();
        bluetoothExecutor.shutdownNow();
        super.handleOnDestroy();
    }

    private static final class BluetoothConnectionException extends Exception {
        private final String code;

        private BluetoothConnectionException(String message, String code) {
            super(message);
            this.code = code;
        }
    }
}
