package nju.ycqian.phonenode;

import android.util.Log;

import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;

public class PhoneNode implements NodeMain {
    private MainActivity context;

    public PhoneNode(MainActivity context) {
        this.context = context;
    }

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("phone_node");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        Log.i("node", "start");
        context.setPublisher(new CompressedImagePublisher(connectedNode));
    }

    @Override
    public void onShutdown(Node node) {
        Log.i("node", "shutdown");
    }

    @Override
    public void onShutdownComplete(Node node) {
        Log.i("node", "shutdown complete");
    }

    @Override
    public void onError(Node node, Throwable throwable) {
        Log.i("node", "error");
    }
}
