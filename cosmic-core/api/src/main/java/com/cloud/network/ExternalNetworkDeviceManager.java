package com.cloud.network;

import com.cloud.api.command.admin.network.AddNetworkDeviceCmd;
import com.cloud.api.command.admin.network.DeleteNetworkDeviceCmd;
import com.cloud.api.command.admin.network.ListNetworkDeviceCmd;
import com.cloud.api.response.NetworkDeviceResponse;
import com.cloud.host.Host;
import com.cloud.utils.component.Manager;

import java.util.ArrayList;
import java.util.List;

public interface ExternalNetworkDeviceManager extends Manager {

    Host addNetworkDevice(AddNetworkDeviceCmd cmd);

    NetworkDeviceResponse getApiResponse(Host device);

    List<Host> listNetworkDevice(ListNetworkDeviceCmd cmd);

    boolean deleteNetworkDevice(DeleteNetworkDeviceCmd cmd);

    class NetworkDevice {
        private static final List<NetworkDevice> supportedNetworkDevices = new ArrayList<>();
        public static final NetworkDevice ExternalDhcp = new NetworkDevice("ExternalDhcp");
        public static final NetworkDevice NiciraNvp = new NetworkDevice("NiciraNvp", Network.Provider.NiciraNvp.getName());
        private final String _name;
        private final String _provider;

        public NetworkDevice(final String deviceName, final String ntwkServiceprovider) {
            _name = deviceName;
            _provider = ntwkServiceprovider;
            supportedNetworkDevices.add(this);
        }

        public NetworkDevice(final String deviceName) {
            this(deviceName, null);
        }

        public static NetworkDevice getNetworkDevice(final String devicerName) {
            for (final NetworkDevice device : supportedNetworkDevices) {
                if (device.getName().equalsIgnoreCase(devicerName)) {
                    return device;
                }
            }
            return null;
        }

        public String getName() {
            return _name;
        }

        public String getNetworkServiceProvder() {
            return _provider;
        }
    }
}
