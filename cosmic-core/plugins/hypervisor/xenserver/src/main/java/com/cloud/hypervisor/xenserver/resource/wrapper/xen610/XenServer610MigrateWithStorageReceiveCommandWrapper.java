package com.cloud.hypervisor.xenserver.resource.wrapper.xen610;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.MigrateWithStorageReceiveAnswer;
import com.cloud.agent.api.MigrateWithStorageReceiveCommand;
import com.cloud.agent.api.to.NicTO;
import com.cloud.agent.api.to.StorageFilerTO;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.agent.api.to.VolumeTO;
import com.cloud.hypervisor.xenserver.resource.XenServer610Resource;
import com.cloud.hypervisor.xenserver.resource.XsHost;
import com.cloud.hypervisor.xenserver.resource.XsLocalNetwork;
import com.cloud.network.Networks.TrafficType;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Host;
import com.xensource.xenapi.Network;
import com.xensource.xenapi.SR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ResourceWrapper(handles = MigrateWithStorageReceiveCommand.class)
public final class XenServer610MigrateWithStorageReceiveCommandWrapper extends CommandWrapper<MigrateWithStorageReceiveCommand, Answer, XenServer610Resource> {

    private static final Logger s_logger = LoggerFactory.getLogger(XenServer610MigrateWithStorageReceiveCommandWrapper.class);

    @Override
    public Answer execute(final MigrateWithStorageReceiveCommand command, final XenServer610Resource xenServer610Resource) {
        final Connection connection = xenServer610Resource.getConnection();
        final VirtualMachineTO vmSpec = command.getVirtualMachine();
        final List<Pair<VolumeTO, StorageFilerTO>> volumeToFiler = command.getVolumeToFiler();

        try {
            // In a cluster management server setup, the migrate with storage receive and send
            // commands and answers may have to be forwarded to another management server. This
            // happens when the host/resource on which the command has to be executed is owned
            // by the second management server. The serialization/deserialization of the command
            // and answers fails as the xapi SR and Network class type isn't understand by the
            // agent attache. Seriliaze the SR and Network objects here to a string and pass in
            // the answer object. It'll be deserialzed and object created in migrate with
            // storage send command execution.
            final Gson gson = new Gson();
            // Get a map of all the SRs to which the vdis will be migrated.
            final List<Pair<VolumeTO, Object>> volumeToSr = new ArrayList<>();
            for (final Pair<VolumeTO, StorageFilerTO> entry : volumeToFiler) {
                final StorageFilerTO storageFiler = entry.second();
                final SR sr = xenServer610Resource.getStorageRepository(connection, storageFiler.getUuid());
                volumeToSr.add(new Pair<>(entry.first(), sr));
            }

            // Get the list of networks to which the vifs will attach.
            final List<Pair<NicTO, Object>> nicToNetwork = new ArrayList<>();
            for (final NicTO nicTo : vmSpec.getNics()) {
                final Network network = xenServer610Resource.getNetwork(connection, nicTo);
                nicToNetwork.add(new Pair<>(nicTo, network));
            }

            final XsLocalNetwork nativeNetworkForTraffic = xenServer610Resource.getNativeNetworkForTraffic(connection, TrafficType.Storage, null);
            final Network network = nativeNetworkForTraffic.getNetwork();
            final XsHost xsHost = xenServer610Resource.getHost();
            final String uuid = xsHost.getUuid();

            final Map<String, String> other = new HashMap<>();
            other.put("live", "true");

            final Host host = Host.getByUuid(connection, uuid);
            final Map<String, String> token = host.migrateReceive(connection, network, other);

            return new MigrateWithStorageReceiveAnswer(command, volumeToSr, nicToNetwork, token);
        } catch (final CloudRuntimeException e) {
            s_logger.error("Migration of vm " + vmSpec.getName() + " with storage failed due to " + e.toString(), e);
            return new MigrateWithStorageReceiveAnswer(command, e);
        } catch (final Exception e) {
            s_logger.error("Migration of vm " + vmSpec.getName() + " with storage failed due to " + e.toString(), e);
            return new MigrateWithStorageReceiveAnswer(command, e);
        }
    }
}