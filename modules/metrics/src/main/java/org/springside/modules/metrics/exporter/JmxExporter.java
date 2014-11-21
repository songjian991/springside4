package org.springside.modules.metrics.exporter;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springside.modules.metrics.Counter;
import org.springside.modules.metrics.Gauge;
import org.springside.modules.metrics.Histogram;
import org.springside.modules.metrics.MetricRegistry;
import org.springside.modules.metrics.Timer;

public class JmxExporter implements MetricRegistryListener {

	private static Logger logger = LoggerFactory.getLogger(JmxExporter.class);
	private MBeanServer mBeanServer;
	private MetricRegistry registry;
	private String domain;
	private final Map<ObjectName, ObjectName> registered;

	public JmxExporter(String domain, MetricRegistry registry) {
		this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
		this.registry = registry;
		this.domain = domain;
		this.registered = new ConcurrentHashMap<ObjectName, ObjectName>();

		registry.addListener(this);
	}

	public void initMBeans() {

		Map<String, Gauge> gauges = registry.getGauges();
		for (Entry<String, Gauge> entry : gauges.entrySet()) {
			onGaugeAdded(entry.getKey(), entry.getValue());
		}

		Map<String, Counter> counters = registry.getCounters();
		for (Entry<String, Counter> entry : counters.entrySet()) {
			onCounterAdded(entry.getKey(), entry.getValue());
		}

		Map<String, Histogram> histograms = registry.getHistograms();
		for (Entry<String, Histogram> entry : histograms.entrySet()) {
			onHistogramAdded(entry.getKey(), entry.getValue());
		}

		Map<String, Timer> timers = registry.getTimers();
		for (Entry<String, Timer> entry : timers.entrySet()) {
			onTimerAdded(entry.getKey(), entry.getValue());
		}
	}

	public void destroyMBeans() {
		Map<String, Gauge> gauges = registry.getGauges();
		for (String key : gauges.keySet()) {
			onGaugeRemoved(key);
		}

		Map<String, Counter> counters = registry.getCounters();
		for (String key : counters.keySet()) {
			onCounterRemoved(key);
		}

		Map<String, Histogram> histograms = registry.getHistograms();
		for (String key : histograms.keySet()) {
			onHistogramRemoved(key);
		}

		Map<String, Timer> timers = registry.getTimers();
		for (String key : timers.keySet()) {
			onTimerRemoved(key);
		}
	}

	private void registerMBean(Object mBean, ObjectName objectName) throws InstanceAlreadyExistsException, JMException {
		ObjectInstance objectInstance = mBeanServer.registerMBean(mBean, objectName);
		if (objectInstance != null) {
			// the websphere mbeanserver rewrites the objectname to include cell, node & server info
			// make sure we capture the new objectName for unregistration
			registered.put(objectName, objectInstance.getObjectName());
		} else {
			registered.put(objectName, objectName);
		}
	}

	private void unregisterMBean(ObjectName originalObjectName) throws InstanceNotFoundException,
			MBeanRegistrationException {
		ObjectName storedObjectName = registered.remove(originalObjectName);
		if (storedObjectName != null) {
			mBeanServer.unregisterMBean(storedObjectName);
		} else {
			mBeanServer.unregisterMBean(originalObjectName);
		}
	}

	private ObjectName createName(String type, String name) {
		try {
			return new ObjectName(this.domain, "name", name);
		} catch (MalformedObjectNameException e) {
			try {
				return new ObjectName(this.domain, "name", ObjectName.quote(name));
			} catch (MalformedObjectNameException e1) {
				logger.warn("Unable to register {} {}", type, name, e1);
				throw new RuntimeException(e1);
			}
		}
	}

	@Override
	public void onGaugeAdded(String name, Gauge gauge) {
		try {
			final ObjectName objectName = createName("gauges", name);
			registerMBean(new JmxGauge(gauge, objectName), objectName);
		} catch (InstanceAlreadyExistsException e) {
			logger.debug("Unable to register gauge", e);
		} catch (JMException e) {
			logger.warn("Unable to register gauge", e);
		}
	}

	@Override
	public void onCounterAdded(String name, Counter counter) {
		try {
			final ObjectName objectName = createName("counters", name);
			registerMBean(new JmxCounter(counter, objectName), objectName);
		} catch (InstanceAlreadyExistsException e) {
			logger.debug("Unable to register counter", e);
		} catch (JMException e) {
			logger.warn("Unable to register counter", e);
		}

	}

	@Override
	public void onHistogramAdded(String name, Histogram histogram) {
		try {
			final ObjectName objectName = createName("histograms", name);
			registerMBean(new JmxHistogram(histogram, objectName), objectName);
		} catch (InstanceAlreadyExistsException e) {
			logger.debug("Unable to register histogram", e);
		} catch (JMException e) {
			logger.warn("Unable to register histogram", e);
		}

	}

	@Override
	public void onTimerAdded(String name, Timer timer) {
		try {
			final ObjectName objectName = createName("timers", name);
			registerMBean(new JmxTimer(timer, objectName), objectName);
		} catch (InstanceAlreadyExistsException e) {
			logger.debug("Unable to register timer", e);
		} catch (JMException e) {
			logger.warn("Unable to register timer", e);
		}

	}

	@Override
	public void onGaugeRemoved(String name) {
		try {
			final ObjectName objectName = createName("guages", name);
			unregisterMBean(objectName);
		} catch (JMException e) {
			logger.warn("Unable to register guage", e);
		}
	}

	@Override
	public void onCounterRemoved(String name) {
		try {
			final ObjectName objectName = createName("counters", name);
			unregisterMBean(objectName);
		} catch (JMException e) {
			logger.warn("Unable to register counter", e);
		}

	}

	@Override
	public void onHistogramRemoved(String name) {
		try {
			final ObjectName objectName = createName("histograms", name);
			unregisterMBean(objectName);
		} catch (JMException e) {
			logger.warn("Unable to register histogram", e);
		}

	}

	@Override
	public void onTimerRemoved(String name) {
		try {
			final ObjectName objectName = createName("timers", name);
			unregisterMBean(objectName);
		} catch (JMException e) {
			logger.warn("Unable to register timer", e);
		}
	}

	public interface MetricMBean {
		ObjectName objectName();
	}

	public interface JmxGaugeMBean extends MetricMBean {
		Number getValue();
	}

	public interface JmxCounterMBean extends MetricMBean {
		long getLastCount();

		double getLastRate();

		long getTotalCount();

		double getTotalRate();
	}

	public interface JmxHistogramMBean extends MetricMBean {

		long getMin();

		long getMax();

		double getMean();
	}

	public interface JmxTimerMBean extends MetricMBean {
		long getLastCount();

		double getLastRate();

		long getTotalCount();

		double getTotalRate();

		long getMin();

		long getMax();

		double getMean();
	}

	private abstract static class AbstractBean implements MetricMBean {
		private final ObjectName objectName;

		AbstractBean(ObjectName objectName) {
			this.objectName = objectName;
		}

		@Override
		public ObjectName objectName() {
			return objectName;
		}
	}

	private static class JmxGauge extends AbstractBean implements JmxGaugeMBean {

		private final Gauge metric;

		public JmxGauge(Gauge gauge, ObjectName objectName) {
			super(objectName);
			this.metric = gauge;
		}

		@Override
		public Number getValue() {
			return metric.snapshot;
		}
	}

	private static class JmxCounter extends AbstractBean implements JmxCounterMBean {
		private final Counter metric;

		private JmxCounter(Counter metric, ObjectName objectName) {
			super(objectName);
			this.metric = metric;
		}

		@Override
		public long getLastCount() {
			return metric.snapshot.lastCount;
		}

		@Override
		public double getLastRate() {
			return metric.snapshot.lastRate;
		}

		@Override
		public long getTotalCount() {
			return metric.snapshot.totalCount;
		}

		@Override
		public double getTotalRate() {
			return metric.snapshot.meanRate;
		}

	}

	private static class JmxHistogram extends AbstractBean implements JmxHistogramMBean {
		private final Histogram metric;

		private JmxHistogram(Histogram metric, ObjectName objectName) {
			super(objectName);
			this.metric = metric;
		}

		@Override
		public long getMin() {
			return metric.snapshot.min;
		}

		@Override
		public long getMax() {
			return metric.snapshot.max;
		}

		@Override
		public double getMean() {
			return metric.snapshot.mean;
		}
	}

	private static class JmxTimer extends AbstractBean implements JmxTimerMBean {
		private final Timer metric;

		private JmxTimer(Timer metric, ObjectName objectName) {
			super(objectName);
			this.metric = metric;
		}

		// TODO: other data
		@Override
		public long getLastCount() {
			return metric.snapshot.counterMetric.lastCount;
		}

		@Override
		public double getLastRate() {
			return metric.snapshot.counterMetric.lastRate;
		}

		@Override
		public long getTotalCount() {
			return metric.snapshot.counterMetric.totalCount;
		}

		@Override
		public double getTotalRate() {
			return metric.snapshot.counterMetric.meanRate;
		}

		@Override
		public long getMin() {
			return metric.snapshot.histogramMetric.min;
		}

		@Override
		public long getMax() {
			return metric.snapshot.histogramMetric.max;
		}

		@Override
		public double getMean() {
			return metric.snapshot.histogramMetric.mean;
		}
	}

}
