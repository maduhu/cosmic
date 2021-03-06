package com.cloud.api.command.user.snapshot;

import com.cloud.acl.RoleType;
import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.SnapshotPolicyResponse;
import com.cloud.api.response.VolumeResponse;
import com.cloud.storage.snapshot.SnapshotPolicy;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listSnapshotPolicies", description = "Lists snapshot policies.", responseObject = SnapshotPolicyResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListSnapshotPoliciesCmd extends BaseListCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListSnapshotPoliciesCmd.class.getName());

    private static final String s_name = "listsnapshotpoliciesresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.VOLUME_ID, type = CommandType.UUID, entityType = VolumeResponse.class, description = "the ID of the disk volume")
    private Long volumeId;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = SnapshotPolicyResponse.class, description = "the ID of the snapshot policy")
    private Long id;

    @Parameter(name = ApiConstants.FOR_DISPLAY, type = CommandType.BOOLEAN, description = "list resources by display flag; only ROOT admin is eligible to pass this parameter",
            since = "4.4", authorized = {RoleType.Admin})
    private Boolean display;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getVolumeId() {
        return volumeId;
    }

    public Long getId() {
        return id;
    }

    @Override
    public void execute() {
        final Pair<List<? extends SnapshotPolicy>, Integer> result = _snapshotService.listPoliciesforVolume(this);
        final ListResponse<SnapshotPolicyResponse> response = new ListResponse<>();
        final List<SnapshotPolicyResponse> policyResponses = new ArrayList<>();
        for (final SnapshotPolicy policy : result.first()) {
            final SnapshotPolicyResponse policyResponse = _responseGenerator.createSnapshotPolicyResponse(policy);
            policyResponse.setObjectName("snapshotpolicy");
            policyResponses.add(policyResponse);
        }
        response.setResponses(policyResponses, result.second());
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }
    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public boolean isDisplay() {
        if (display != null) {
            return display;
        }
        return true;
    }
}
