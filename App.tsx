import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, View, Button, TextInput, Alert, ScrollView } from 'react-native';
import NebulaModule from './src/bridges/NebulaModule';

export default function App() {
    const [status, setStatus] = useState('Checking status...');
    const [isConnected, setIsConnected] = useState(false);
    const [pingTarget, setPingTarget] = useState('100.64.0.1');
    const [pingResult, setPingResult] = useState('');

    // Your Nebula configuration - this is the same as in your Flutter app
    const nebulaConfig = `pki:
  ca: |
    -----BEGIN NEBULA CERTIFICATE-----
    CkYKFENPU0dyaWQgTmV0d29ya3MgSW5jKP7v8bkGMP7W9sgGOiC8X6eDBiCOTw7S
    NCbpOjVSzbWhUW/frowc/LfvN5S2j0ABEkAotkfzRmBTcbTraoHNQai092Tjmq3r
    rC9z74dKYFLynxew7F/Q3Zp8+6e8vrksJ60ux4DOXbwgzR8n+UaJweIE
    -----END NEBULA CERTIFICATE-----
  cert: |
    -----BEGIN NEBULA CERTIFICATE-----
    CnkKD1Rlc3RAVGVzdFRlbmFudBIKu4eAogaAgPz/DyIKVGVzdFRlbmFudCjBwMC+
    BjD91vbIBjogtpjUjn00yZCK44fIs+hLvRi7nkSGXBTsUoD9EoVedDZKIOMILLts
    pva1JgGN9p837LZqMHsCVq/unyurPnMbKlHkEkAaIHEH40cCJhYBe9tONjMDK4FA
    DHD26AdAST1sSwDlizl3QVxa5xv0mkiOHqDMydjL5fmXIyjacMu4+7nI2zIn
    -----END NEBULA CERTIFICATE-----
  key: null  # Will be provided at runtime as a separate parameter

static_host_map:
  "100.64.0.1": ["mza.cosgrid.net:4242"]

lighthouse:
  am_lighthouse: false
  serve_dns: false
  interval: 60
  hosts:
    - "100.64.0.1"

listen:
  host: "0.0.0.0"
  port: 4242

# ... rest of your config from the Flutter app
`;

    // Your private key
    const privateKey = `-----BEGIN NEBULA X25519 PRIVATE KEY-----
q8UyTb2Xq3rx6srp/bMH5H3dHJAcz9RvWoX15ezOxbU=
-----END NEBULA X25519 PRIVATE KEY-----`;

    useEffect(() => {
        // Check connection status on component mount
        checkVpnStatus();

        // Set up interval to check status periodically
        const intervalId = setInterval(checkVpnStatus, 5000);

        // Clean up interval on unmount
        return () => clearInterval(intervalId);
    }, []);

    const checkVpnStatus = async () => {
        try {
            const connected = await NebulaModule.checkConnectionStatus();
            setIsConnected(connected);
            setStatus(connected ? 'Connected' : 'Disconnected');
        } catch (error) {
            console.error('Error checking VPN status:', error);
            setStatus('Error checking status');
        }
    };

    const connectVPN = async () => {
        setStatus('Connecting...');
        try {
            // Test the configuration first
            const isValid = await NebulaModule.testConfig(nebulaConfig, privateKey);

            if (isValid) {
                const success = await NebulaModule.startNebula(nebulaConfig, privateKey);
                setIsConnected(success);
                setStatus(success ? 'Connected' : 'Connection failed');
            } else {
                setStatus('Invalid configuration');
            }
        } catch (error: any) {
            console.error('Error connecting to VPN:', error);
            setStatus(`Error: ${error.message}`);
        }
    };

    const disconnectVPN = async () => {
        setStatus('Disconnecting...');
        try {
            const success = await NebulaModule.stopNebula();
            setIsConnected(!success);
            setStatus(success ? 'Disconnected' : 'Failed to disconnect');
        } catch (error: any) {
            console.error('Error disconnecting from VPN:', error);
            setStatus(`Error: ${error.message}`);
        }
    };

    const pingHost = async () => {
        if (!pingTarget) {
            setPingResult('Please enter an IP address');
            return;
        }

        setPingResult('Pinging...');
        try {
            const success = await NebulaModule.pingHost(pingTarget);
            setPingResult(
                success
                    ? `Ping to ${pingTarget} was successful`
                    : `Ping to ${pingTarget} failed`
            );
        } catch (error: any) {
            console.error('Error pinging host:', error);
            setPingResult(`Error: ${error.message}`);
        }
    };

    return (
        <ScrollView contentContainerStyle={styles.container}>
            <Text style={styles.title}>Nebula VPN</Text>

            {/* Status Section */}
            <View style={styles.card}>
                <Text style={[styles.statusIcon, isConnected ? styles.connected : styles.disconnected]}>
                    {isConnected ? '✓' : '✗'}
                </Text>
                <Text style={styles.status}>{status}</Text>
                <Button
                    title={isConnected ? 'Disconnect' : 'Connect'}
                    onPress={isConnected ? disconnectVPN : connectVPN}
                    color={isConnected ? '#FF3B30' : '#34C759'}
                />
            </View>

            {/* Ping Section */}
            <View style={styles.card}>
                <Text style={styles.sectionTitle}>Ping Test</Text>
                <TextInput
                    style={styles.input}
                    value={pingTarget}
                    onChangeText={setPingTarget}
                    placeholder="Enter IP to ping"
                />
                <Button
                    title="Ping"
                    onPress={pingHost}
                    disabled={!isConnected}
                />
                {pingResult ? <Text style={styles.pingResult}>{pingResult}</Text> : null}
            </View>

            {/* Manual refresh button */}
            <Button
                title="Refresh Connection Status"
                onPress={checkVpnStatus}
            />
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: {
        flexGrow: 1,
        padding: 16,
        backgroundColor: '#F5F5F5',
    },
    title: {
        fontSize: 24,
        fontWeight: 'bold',
        marginBottom: 20,
        textAlign: 'center',
    },
    card: {
        backgroundColor: 'white',
        borderRadius: 10,
        padding: 20,
        marginBottom: 20,
        elevation: 2,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
    },
    statusIcon: {
        fontSize: 48,
        textAlign: 'center',
        marginBottom: 10,
    },
    connected: {
        color: '#34C759',
    },
    disconnected: {
        color: '#FF3B30',
    },
    status: {
        fontSize: 18,
        fontWeight: 'bold',
        textAlign: 'center',
        marginBottom: 20,
    },
    sectionTitle: {
        fontSize: 18,
        fontWeight: 'bold',
        marginBottom: 15,
    },
    input: {
        borderWidth: 1,
        borderColor: '#CCCCCC',
        borderRadius: 5,
        padding: 10,
        marginBottom: 15,
    },
    pingResult: {
        marginTop: 15,
        fontSize: 16,
    },
});