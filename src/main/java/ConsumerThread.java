import org.apache.kafka.clients.consumer.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class ConsumerThread implements Runnable {
    private static final Logger log = LogManager.getLogger(KafkaConsumerTestAssignor.class);
    static double eventsViolating = 0;
    static double eventsNonViolating = 0;
    static double totalEvents = 0;
    public static KafkaConsumer<String, Customer> consumer = null;
    static float maxConsumptionRatePerConsumer = 0.0f;
    static float ConsumptionRatePerConsumerInThisPoll = 0.0f;
    static float averageRatePerConsumerForGrpc = 0.0f;

    static long pollsSoFar = 0;
    static Double maxConsumptionRatePerConsumer1 = 0.0d;
    Long[] waitingTimes = new Long[10];

    @Override
    public void run() {
        KafkaConsumerConfig config = KafkaConsumerConfig.fromEnv();
        log.info(KafkaConsumerConfig.class.getName() + ": {}", config.toString());
        Properties props = KafkaConsumerConfig.createProperties(config);
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, LagBasedPartitionAssignor.class.getName());
        //props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, BinPackPartitionAssignor.class.getName());
        //props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName());
         //props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, StickyAssignor.class.getName());
        boolean commit = !Boolean.parseBoolean(config.getEnableAutoCommit());
        consumer = new KafkaConsumer<String, Customer>(props);
        consumer.subscribe(Collections.singletonList(config.getTopic()));
        log.info("Subscribed to topic {}", config.getTopic());

        // int warmup = 0;

        while (true) {
            Long timeBeforePolling = System.currentTimeMillis();
            ConsumerRecords<String, Customer> records = consumer.poll(Duration.ofMillis(Long.MAX_VALUE));
            //ConsumerRecords<String, Customer> records = consumer.poll(Duration.ofMillis(0));
            if (records.count() != 0) {

                for (ConsumerRecord<String, Customer> record : records) {

                    totalEvents++;
                    log.info("System.currentTimeMillis() - record.timestamp() {}"
                            , System.currentTimeMillis() - record.timestamp());
                    if (System.currentTimeMillis() - record.timestamp() <= 5000) {

                        eventsNonViolating++;
                    }else {
                        eventsViolating++;
                    }
                    //TODO sleep per record or per batch
                   /* try {
                        Thread.sleep(Long.parseLong(config.getSleep()));
                        log.info("Sleeping for {}", config.getSleep());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                }
                 try {
                        Thread.sleep(1000);
                        log.info("Sleeping for {}", config.getSleep());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                //getProcessingLatencyForEachEvent(records);
                if (commit) {
                    consumer.commitSync();
                }
                log.info("In this poll, received {} events", records.count());
                Long timeAfterPollingProcessingAndCommit = System.currentTimeMillis();
                ConsumptionRatePerConsumerInThisPoll = ((float) records.count() /
                        (float) (timeAfterPollingProcessingAndCommit - timeBeforePolling)) * 1000.0f;
                pollsSoFar += 1;
                averageRatePerConsumerForGrpc = averageRatePerConsumerForGrpc +
                        (ConsumptionRatePerConsumerInThisPoll - averageRatePerConsumerForGrpc) / (float) (pollsSoFar);

                if (maxConsumptionRatePerConsumer < ConsumptionRatePerConsumerInThisPoll) {
                    maxConsumptionRatePerConsumer = ConsumptionRatePerConsumerInThisPoll;
                }
                maxConsumptionRatePerConsumer1 = Double.parseDouble(String.valueOf(averageRatePerConsumerForGrpc));
                log.info("ConsumptionRatePerConsumerInThisPoll in this poll {}", ConsumptionRatePerConsumerInThisPoll);
                log.info("maxConsumptionRatePerConsumer {}", maxConsumptionRatePerConsumer);

                log.info("averageRatePerConsumerForGrpc  {}", averageRatePerConsumerForGrpc);
                double percentViolating = (double) eventsViolating/(double)totalEvents;
                double percentNonViolating = (double) eventsNonViolating/(double)totalEvents;
                log.info("Percent violating so far {}", percentViolating);
                log.info("Percent non violating so far {}", percentNonViolating);
                log.info("total events {}", totalEvents);
            }
        }
    }



    private static void getProcessingLatencyForEachEvent(ConsumerRecords<String, Customer> records) {

        for (ConsumerRecord<String, Customer> record : records) {
            totalEvents++;
            log.info("System.currentTimeMillis() - record.timestamp() {}"
                    , System.currentTimeMillis() - record.timestamp());
            if (System.currentTimeMillis() - record.timestamp() <= 5000) {

                eventsNonViolating++;
            }else {
                eventsViolating++;
            }
        }

        double percentViolating = (double) eventsViolating/(double)totalEvents;
        double percentNonViolating = (double) eventsNonViolating/(double)totalEvents;


        log.info("Percent violating so far {}", percentViolating);
        log.info("Percent non violating so far {}", percentNonViolating);
        log.info("total events {}", totalEvents);

    }
}
