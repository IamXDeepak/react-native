import { NativeModules } from 'react-native';

const { NebulaBridge } = NativeModules;

if (!NebulaBridge) {
    console.error('NebulaBridge native module is not available');
}

const NebulaModule = {
    /**
     * Starts the Nebula VPN with the given configuration
     */
    startNebula: (config: string, key: string): Promise<boolean> => {
        return NebulaBridge.startNebula(config, key);
    },

    /**
     * Stops the Nebula VPN
     */
    stopNebula: (): Promise<boolean> => {
        return NebulaBridge.stopNebula();
    },

    /**
     * Tests if the provided configuration is valid
     */
    testConfig: (config: string, key: string): Promise<boolean> => {
        return NebulaBridge.testConfig(config, key);
    },

    /**
     * Gets a list of all connected hosts in the Nebula network
     */
    getHostmap: (): Promise<Record<string, any>> => {
        return NebulaBridge.getHostmap().then(JSON.parse);
    },

    /**
     * Checks the current connection status of the VPN
     */
    checkConnectionStatus: (): Promise<boolean> => {
        return NebulaBridge.checkConnectionStatus();
    },

    /**
     * Forces a rebind of the UDP listener (useful when network changes)
     */
    rebindNebula: (reason: string): Promise<boolean> => {
        return NebulaBridge.rebindNebula(reason);
    },

    /**
     * Pings a host in the Nebula network
     */
    pingHost: (host: string): Promise<boolean> => {
        return NebulaBridge.pingHost(host);
    }
};

export default NebulaModule;