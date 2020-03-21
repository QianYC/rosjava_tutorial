package nju.ycqian.phonenode;

import android.util.Log;

import org.ros.exception.RemoteException;
import org.ros.exception.ServiceNotFoundException;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.service.ServiceClient;
import org.ros.node.service.ServiceResponseListener;

import pi_robot.SrvTrigger;
import pi_robot.SrvTriggerRequest;
import pi_robot.SrvTriggerResponse;

public class StateClient extends AbstractNodeMain {
    private ServiceClient<SrvTriggerRequest, SrvTriggerResponse> client;

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("state_client");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        try {
            client = connectedNode.newServiceClient("/change_state", SrvTrigger._TYPE);
        } catch (ServiceNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void changeState(boolean value) {
        SrvTriggerRequest request = client.newMessage();
        request.setState(value);
        client.call(request, new ServiceResponseListener<SrvTriggerResponse>() {
            @Override
            public void onSuccess(SrvTriggerResponse srvTriggerResponse) {
                Log.i("StateClient", "change state success!");
            }

            @Override
            public void onFailure(RemoteException e) {
                Log.i("StateClient", "change state failed!");
            }
        });
    }

}
