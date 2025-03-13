import { NativeModules } from 'react-native';

const { GoBridge } = NativeModules;
console.log("Available native modules:", Object.keys(NativeModules));

// Using a hybrid approach that works both with and without the native module
const GoModule = {
    add: async (a: number, b: number): Promise<number> => {
        try {
            // Try using the native module first
            if (GoBridge && typeof GoBridge.add === 'function') {
                console.log("Using native Go module for add");
                return await GoBridge.add(a, b);
            } else {
                // Fall back to HTTP approach
                console.log("Falling back to HTTP for add");
                const response = await fetch(`http://10.0.2.2:8080/add?a=${a}&b=${b}`);
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const data = await response.json();
                return data.result;
            }
        } catch (error) {
            console.error("Error in add function:", error);
            throw error;
        }
    },

    helloWorld: async (): Promise<string> => {
        try {
            // Try using the native module first
            if (GoBridge && typeof GoBridge.helloWorld === 'function') {
                console.log("Using native Go module for helloWorld");
                return await GoBridge.helloWorld();
            } else {
                // Fall back to HTTP approach
                console.log("Falling back to HTTP for helloWorld");
                const response = await fetch(`http://10.0.2.2:8080/hello`);
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const data = await response.json();
                return data.message;
            }
        } catch (error) {
            console.error("Error in helloWorld function:", error);
            throw error;
        }
    },
};

export default GoModule;