package nju.ycqian.phonenode;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;
import android.util.Size;

import com.google.common.base.Preconditions;

import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.ros.internal.message.MessageBuffers;
import org.ros.message.Time;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;

import sensor_msgs.CompressedImage;

public class CompressedImagePublisher implements ImagePublisher {
    private final ConnectedNode connectedNode;
    private final Publisher<CompressedImage> imagePublisher;
    private final Publisher<sensor_msgs.CameraInfo> cameraInfoPublisher;

    private byte[] rawImageBuffer;
    private Size rawImageSize;
    private YuvImage yuvImage;
    private Rect rect;
    private ChannelBufferOutputStream stream;

    public CompressedImagePublisher(ConnectedNode connectedNode) {
        this.connectedNode = connectedNode;
        NameResolver resolver = connectedNode.getResolver().newChild("camera");
        imagePublisher =
                connectedNode.newPublisher(resolver.resolve("image/compressed"),
                        sensor_msgs.CompressedImage._TYPE);
        cameraInfoPublisher =
                connectedNode.newPublisher(resolver.resolve("camera_info"), sensor_msgs.CameraInfo._TYPE);
        stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
    }

    @Override
    public void onNewImage(byte[] data, Size size) {
        Log.i("onNewRawImage", Thread.currentThread().getName());
        Preconditions.checkNotNull(data);
        Preconditions.checkNotNull(size);
        if (data != rawImageBuffer || !size.equals(rawImageSize)) {
            rawImageBuffer = data;
            rawImageSize = size;
            yuvImage = new YuvImage(rawImageBuffer, ImageFormat.NV21, size.getWidth(), size.getHeight(), null);
            rect = new Rect(0, 0, size.getWidth(), size.getHeight());
        }

        Time currentTime = connectedNode.getCurrentTime();
        String frameId = "camera";

        sensor_msgs.CompressedImage image = imagePublisher.newMessage();
        image.setFormat("jpeg");
        image.getHeader().setStamp(currentTime);
        image.getHeader().setFrameId(frameId);

        Preconditions.checkState(yuvImage.compressToJpeg(rect, 20, stream));
        image.setData(stream.buffer().copy());
        stream.buffer().clear();

        imagePublisher.publish(image);

        sensor_msgs.CameraInfo cameraInfo = cameraInfoPublisher.newMessage();
        cameraInfo.getHeader().setStamp(currentTime);
        cameraInfo.getHeader().setFrameId(frameId);

        cameraInfo.setWidth(size.getWidth());
        cameraInfo.setHeight(size.getHeight());
        cameraInfoPublisher.publish(cameraInfo);
    }
}
