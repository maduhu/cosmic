package com.cloud.api.command.admin.vpc;

import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.NetworkACLResponse;
import com.cloud.api.response.NetworkOfferingResponse;
import com.cloud.api.response.PhysicalNetworkResponse;
import com.cloud.api.response.PrivateGatewayResponse;
import com.cloud.api.response.VpcResponse;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.vpc.PrivateGateway;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.VpcGateway;
import com.cloud.user.Account;
import com.cloud.utils.exception.InvalidParameterValueException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createPrivateGateway", description = "Creates a private gateway", responseObject = PrivateGatewayResponse.class, entityType = {VpcGateway.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreatePrivateGatewayCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreatePrivateGatewayCmd.class.getName());

    private static final String s_name = "createprivategatewayresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.PHYSICAL_NETWORK_ID,
            type = CommandType.UUID,
            entityType = PhysicalNetworkResponse.class,
            description = "the Physical Network ID the network belongs to")
    private Long physicalNetworkId;

    @Parameter(name = ApiConstants.GATEWAY, type = CommandType.STRING, required = true, description = "the gateway of the Private gateway")
    private String gateway;

    @Parameter(name = ApiConstants.NETMASK, type = CommandType.STRING, required = true, description = "the netmask of the Private gateway")
    private String netmask;

    @Parameter(name = ApiConstants.IP_ADDRESS, type = CommandType.STRING, required = true, description = "the IP address of the Private gateaway")
    private String ipAddress;

    @Parameter(name = ApiConstants.VLAN, type = CommandType.STRING, required = true, description = "the network implementation uri for the private gateway")
    private String broadcastUri;

    @Parameter(name = ApiConstants.NETWORK_OFFERING_ID,
            type = CommandType.UUID,
            required = false,
            entityType = NetworkOfferingResponse.class,
            description = "the uuid of the network offering to use for the private gateways network connection")
    private Long networkOfferingId;

    @Parameter(name = ApiConstants.VPC_ID, type = CommandType.UUID, entityType = VpcResponse.class, required = true, description = "the VPC network belongs to")
    private Long vpcId;

    @Parameter(name = ApiConstants.SOURCE_NAT_SUPPORTED,
            type = CommandType.BOOLEAN,
            required = false,
            description = "source NAT supported value. Default value false. If 'true' source NAT is enabled on the private gateway"
                    + " 'false': sourcenat is not supported")
    private Boolean isSourceNat;

    @Parameter(name = ApiConstants.ACL_ID, type = CommandType.UUID, entityType = NetworkACLResponse.class, required = false, description = "the ID of the network ACL")
    private Long aclId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void create() throws ResourceAllocationException {
        PrivateGateway result = null;
        try {
            result =
                    _vpcService.createVpcPrivateGateway(getVpcId(), getPhysicalNetworkId(), getBroadcastUri(), getStartIp(), getGateway(), getNetmask(), getEntityOwnerId(),
                            getNetworkOfferingId(), getIsSourceNat(), getAclId());
        } catch (final InsufficientCapacityException ex) {
            s_logger.info(ex.toString());
            throw new ServerApiException(ApiErrorCode.INSUFFICIENT_CAPACITY_ERROR, ex.getMessage());
        } catch (final ConcurrentOperationException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }

        if (result != null) {
            setEntityId(result.getId());
            setEntityUuid(result.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create private gateway");
        }
    }

    public Long getVpcId() {
        return vpcId;
    }

    public Long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    public String getBroadcastUri() {
        return broadcastUri;
    }

    public String getStartIp() {
        return ipAddress;
    }

    public String getGateway() {
        return gateway;
    }

    public String getNetmask() {
        return netmask;
    }

    private Long getNetworkOfferingId() {
        return networkOfferingId;
    }

    public Boolean getIsSourceNat() {
        if (isSourceNat == null) {
            return false;
        }
        return isSourceNat;
    }

    public Long getAclId() {
        return aclId;
    }

    @Override
    public void execute() throws InsufficientCapacityException, ConcurrentOperationException, ResourceAllocationException, ResourceUnavailableException {
        final PrivateGateway result = _vpcService.applyVpcPrivateGateway(getEntityId(), true);
        if (result != null) {
            final PrivateGatewayResponse response = _responseGenerator.createPrivateGatewayResponse(result);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create private gateway");
        }
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_PRIVATE_GATEWAY_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "Applying VPC private gateway. Private gateway Id: " + getEntityId();
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.PrivateGateway;
    }

    @Override
    public String getSyncObjType() {
        return BaseAsyncCmd.vpcSyncObject;
    }

    @Override
    public Long getSyncObjId() {
        final Vpc vpc = _entityMgr.findById(Vpc.class, vpcId);
        if (vpc == null) {
            throw new InvalidParameterValueException("Invalid id is specified for the vpc");
        }
        return vpc.getId();
    }
}