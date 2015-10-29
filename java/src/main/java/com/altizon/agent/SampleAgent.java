
package com.altizon.agent;

import io.datonis.sdk.AliotGateway;
import io.datonis.sdk.DataStream;
import io.datonis.sdk.InstructionHandler;
import io.datonis.sdk.exception.IllegalStreamException;
import io.datonis.sdk.message.AlertType;
import io.datonis.sdk.message.AliotInstruction;

import java.lang.management.ManagementFactory;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.OperatingSystemMXBean;

/**
 * A sample program that sends a stream of events to Datonis
 * 
 * @author Ranjit Nair (ranjit@altizon.com)
 */
public class SampleAgent {
    private static final Logger logger = LoggerFactory.getLogger(SampleAgent.class);
    private static int NUM_EVENTS = 50;

    private DataStream stream;
    private AliotGateway gateway;

    private SampleAgent() {
        // Construct the Gateway that helps us send various types of events to
        // Datonis
        this.gateway = new AliotGateway();
    }

    /**
     * Main Entry Point in the program
     * 
     * @param args
     * @throws IllegalStreamException
     */
    public static void main(String[] args) throws IllegalStreamException {
        // First make sure you create an aliot.properties file using your downloaded access and secret keys.
        // A sample file should be available with this package
        // The keys are available in the Datonis platform portal (Please contact your Account Administrator).

        logger.info("Starting the Sample Aliot Agent");
        // Create and Initialize the agent
        SampleAgent agent = new SampleAgent();

        // Start the Gateway
        if (agent.startGateway()) {
            logger.info("Agent started Successfully, setting up bidirectional communication");

            // Setup bidirectional communication - Needs to use MQTT mode for communication
            // Also, it needs your stream to be configured accordingly in the start gateway
            agent.setupBiDirectionalCommunication();

            logger.info("Bidirectional communication is setup with Datonis");

            // Enable this condition if you want to send a few example alerts
            if (false) {
                logger.info("Transmitting Demo Alerts");
                agent.transmitDemoAlerts();
            }

            // Stream a few sample simulated data events
            logger.info("Transmitting Demo Data");
            agent.transmitData();
            logger.info("Transmitted Demo data");

            logger.info("Exiting");
            agent.stopGateway();
        } else {
            logger.error("Could not start Sample Aliot Agent. Please check aliot.log for more details");
        }
    }

    private boolean startGateway() {
        try {
            // Decide what the metadata format of your stream should be. 
            // This will show up as the stream parameters that you are pushing on Datonis.
            // In this example we will be pushing cpu and memory utilization.
            // We therefore declare them in a JSON schema format.
            // Obviously escaped so that java does not complain
            String metadata = "{\"cpu\": {\"type\":\"number\"}, \"mem\": {\"type\":\"number\"}}";

            // Create a Stream Object.
            // It is extremely important to have unique Stream keys or collisions may occur and data could get overwritten.
            // Please create a DataStream on the Datonis portal and use the key here.
            // Stream names are non-unique, however uniqueness is recommended.
            // Use a logical 'type' to describe the Data Stream. For instance, System Monitor in this case.
            // Multiple streams can exist for a type.
            // This constructor will throw an illegal stream exception if conditions are not met.
            stream = new DataStream("stream_key", "SysMon", "System Monitor", "A monitor for CPU and Memory", metadata);

            // Comment the line earlier and un-comment this line if you want this stream to be bi-directional i.e. supports receiving instructions (Note: Only works with MQTT/MQTTs)
            // stream = new DataStream("stream_key", "SysMon", "System Monitor", "A monitor for CPU and Memory", metadata, true);

            // You can register multiple streams and send data for them.
            // First add the streams and then call register.
            // In this case, there is only a single stream object.
            gateway.addDataStream(stream);

            gateway.start();
        } catch (IllegalStreamException e) {
            logger.error("Could not start the aliot gateway: ", e.getMessage(), e);
            return false;
        }
        return true;
    }

    private void stopGateway() {
        gateway.stop();
    }

    private void setupBiDirectionalCommunication() {
        gateway.setInstructionHandler(new InstructionHandler() {

            @Override
            public void handleInstruction(AliotGateway gateway, AliotInstruction instruction) {
                logger.info("Received instruction for stream: " + instruction.getStreamKey()
                        + " from Datonis: " + instruction.getInstruction().toJSONString());
                JSONObject data = new JSONObject();
                data.put("execution_status", "success");
                if (!gateway.transmitAlert(instruction.getAlertKey(), instruction.getStreamKey(), AlertType.WARNING, "Demo warning, instruction received and logged!", data)) {
                    logger.error("Could not send Acknowlegement for instruction back to datonis.");
                } else {
                    logger.info("Sent an instruction acknowlegement back to Datonis!");
                }
            }
        });
    }

    private JSONObject getSystemInfo() {
        OperatingSystemMXBean os = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        long mem = (os.getFreePhysicalMemorySize() / (1024 * 1024));
        // Not accurate but sufficient for demonstration
        double cpu = ((os.getSystemLoadAverage() / os.getAvailableProcessors()) * 10);

        JSONObject obj = new JSONObject();
        obj.put("cpu", cpu);
        obj.put("mem", mem);
        return obj;
    }

    private void transmitAlert(AlertType alertType) {
        JSONObject data = new JSONObject();
        data.put("demoKey", "demoValue");

        if (!gateway.transmitAlert(stream.getKey(), alertType, "This is an example " + alertType.toString() + " alert!", data)) {
            logger.info("Sent example " + alertType.toString() + " alert!");
        } else {
            logger.error("Could not send example " + alertType.toString() + " alert");
        }
    }

    public void transmitDemoAlerts() {
        transmitAlert(AlertType.INFO);
        transmitAlert(AlertType.WARNING);
        transmitAlert(AlertType.ERROR);
        transmitAlert(AlertType.CRITICAL);
    }

    public void transmitData() throws IllegalStreamException {
        int heartbeat = 0;
        for (int count = 1; count <= NUM_EVENTS; count++) {
            // Construct the JSON packet to be sent. This has to match the
            // metadata structure.
            JSONObject data = getSystemInfo();
            // Transmit the data. There is also a method to
            // specify the timestamp of the data packet if the transmission is delayed. 
            // The syntax is gateway.transmitData(stream, data, timestamp)
            if (!gateway.transmitData(stream, data)) {
                logger.warn("Could not transmit packet : " + count + " value " + data.toJSONString());
            }

            heartbeat++;
            if (heartbeat == 300) {
                gateway.transmitHeartbeat();
                heartbeat = 0;
            }
            try {
                Thread.currentThread().sleep(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}