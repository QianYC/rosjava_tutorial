package nju.ycqian.phonenode;

import android.util.Log;

import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.service.ServiceResponseBuilder;

import pi_robot.SrvCapture;
import pi_robot.SrvCaptureRequest;
import pi_robot.SrvCaptureResponse;


public class CameraServer extends AbstractNodeMain {
    private MainActivity context;

    public CameraServer(MainActivity context) {
        this.context = context;
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("camera_server");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        connectedNode.newServiceServer("/camera_service", SrvCapture._TYPE,
                (ServiceResponseBuilder<SrvCaptureRequest, SrvCaptureResponse>) (request, response) -> {
                    Log.i("CameraServer on thread : ", Thread.currentThread().getName());
                    context.captureCallback(request.getX(), request.getY());
                    response.setSuccess(true);
                });
    }
}
