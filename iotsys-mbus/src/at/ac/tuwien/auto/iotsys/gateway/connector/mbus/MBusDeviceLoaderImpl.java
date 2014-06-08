/*******************************************************************************
 * Copyright (c) 2013
 * Institute of Computer Aided Automation, Automation Systems Group, TU Wien.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * 
 * This file is part of the IoTSyS project.
 ******************************************************************************/

package at.ac.tuwien.auto.iotsys.gateway.connector.mbus;

import java.lang.reflect.Constructor;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import obix.Obj;
import obix.Uri;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import at.ac.tuwien.auto.iotsys.commons.Connector;
import at.ac.tuwien.auto.iotsys.commons.Device;
import at.ac.tuwien.auto.iotsys.commons.DeviceLoader;
import at.ac.tuwien.auto.iotsys.commons.ObjectBroker;
import at.ac.tuwien.auto.iotsys.commons.persistent.DeviceConfigs;

import com.fasterxml.jackson.databind.JsonNode;

public class MBusDeviceLoaderImpl implements DeviceLoader {
	
	private static Logger log = Logger.getLogger(MBusDeviceLoaderImpl.class
			.getName());

	private XMLConfiguration devicesConfig;
	
	private ArrayList<Obj> myObjects = new ArrayList<Obj>();

	@Override
	public ArrayList<Connector> initDevices(ObjectBroker objectBroker) {
		setConfiguration(devicesConfig);
		
		ArrayList<Connector> connectors = new ArrayList<Connector>();

		int connectorsSize = 0;
		// WMBus
		Object mbusConnectors = devicesConfig
				.getProperty("mbus.connector.name");
		if (mbusConnectors != null) {
			connectorsSize = 1;
		} else {
			connectorsSize = 0;
		}

		if (mbusConnectors instanceof Collection<?>) {
			connectorsSize = ((Collection<?>) mbusConnectors).size();
		}

		for (int connector = 0; connector < connectorsSize; connector++) {
			HierarchicalConfiguration subConfig = devicesConfig
					.configurationAt("mbus.connector(" + connector + ")");

			Object mbusConfiguredDevices = subConfig
					.getProperty("device.type");
			String connectorName = subConfig.getString("name");
			String serialPort = subConfig.getString("serialPort");
			Boolean enabled = subConfig.getBoolean("enabled", false);

			if (enabled) {
				try {
					MBusConnector mbusConnector = new MBusConnector(
							serialPort);
					mbusConnector.setEnabled(enabled);
					mbusConnector.setName(connectorName);
					mbusConnector.setTechnology("mbus");
					mbusConnector.setSerialPort(serialPort);
					
					//mbusConnector.connect();
					connectors.add(mbusConnector);

					int mbusDevicesCount = 0;
					if (mbusConfiguredDevices instanceof Collection<?>) {
						Collection<?> mbusDevice = (Collection<?>) mbusConfiguredDevices;
						mbusDevicesCount = mbusDevice.size();

					} else if (mbusConfiguredDevices != null) {
						mbusDevicesCount = 1;
					}

					log.info(mbusDevicesCount
							+ " MBus devices found in configuration for connector "
							+ connectorName);

					List<Device> ds = new ArrayList<Device>();
					for (int i = 0; i < mbusDevicesCount; i++) {
						String type = subConfig.getString("device(" + i
								+ ").type");
						Integer address = subConfig.getInteger("device(" + i
								+ ").address",0);
						String addressString = subConfig.getString("device("
								+ i + ").address");
						Integer interval = subConfig.getInteger("device(" + i
								+ ").interval", 0);
						String serialnr = subConfig.getString("device(" + i
								+ ").serialnr");
						String ipv6 = subConfig.getString("device(" + i
								+ ").ipv6");
						String href = subConfig.getString("device(" + i
								+ ").href");						
						String name = subConfig.getString("device(" + i
								+ ").name");

						Boolean historyEnabled = subConfig.getBoolean("device("
								+ i + ").historyEnabled", false);
						
						Boolean groupCommEnabled = subConfig.getBoolean("device("
								+ i + ").groupCommEnabled", false);

						Integer historyCount = subConfig.getInt("device(" + i
								+ ").historyCount", 0);						
						
						// Transition step: comment when done
						JsonNode thisConnector = DeviceConfigs.getInstance()
								.getConnectors("mbus")
								.get(connector);
						Device d = new Device(type, ipv6, addressString, href, name, null, historyCount, historyEnabled, groupCommEnabled, null);
						d.setConnectorId(thisConnector.get("_id").asText());
						ds.add(d);
						
						if(interval > 0){
							mbusConnector.setInterval(interval);
						}
						
						if(address > 0 && address <= 255){
							mbusConnector.setAdress((byte)(address&0xFF));;
						}

						if (type != null && serialnr != null && address != null) {
//							String serialNr = (String) address;
//							String aesKey = (String) address.get(1);

							Object[] args = new Object[2];
							args[0] = mbusConnector;
							args[1] = serialnr;
							//args[2] = address;							
//							args[2] = aesKey;

							try {

								Constructor<?>[] declaredConstructors = Class
										.forName(type)
										.getDeclaredConstructors();
								for (int k = 0; k < declaredConstructors.length; k++) {
									if (declaredConstructors[k]
											.getParameterTypes().length == args.length) { // constructor
																							// that
																							// takes
																							// the
																							// KNX
																							// connector
																							// and
																							// group
																							// address
																							// as
																							// argument
										Obj smartMeter = (Obj) declaredConstructors[k]
												.newInstance(args); // create
																	// a
																	// instance
																	// of
																	// the
																	// specified
																	// KNX
																	// device
										smartMeter.setHref(new Uri(URLEncoder.encode(connectorName, "UTF-8") + "/" + href));
										
										if (ipv6 != null) {
											objectBroker.addObj(smartMeter, ipv6);
										} else {
											objectBroker.addObj(smartMeter);
										}
										
										if(name != null && name.length() > 0){
											smartMeter.setName(name);
										}
										
										synchronized (myObjects) {
											myObjects.add(smartMeter);
										}
										smartMeter.initialize();
									
										if (historyEnabled != null
												&& historyEnabled) {
											if (historyCount != null
													&& historyCount != 0) {
												objectBroker
														.addHistoryToDatapoints(
																smartMeter,
																historyCount);
											} else {
												objectBroker
														.addHistoryToDatapoints(smartMeter);
											}
										}
										
										if(groupCommEnabled != null && groupCommEnabled){
											objectBroker.enableGroupComm(smartMeter);
										}
									}
								}
							} catch (SecurityException e) {
								e.printStackTrace();
							} catch (ClassNotFoundException e) {
								e.printStackTrace();
							}
						}
					}
					DeviceConfigs.getInstance().addDevices(ds);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return connectors;
	}

	@Override
	public void removeDevices(ObjectBroker objectBroker) {
		synchronized(myObjects) {
			for(Obj obj : myObjects) {
				objectBroker.removeObj(obj.getFullContextPath());
			}
		}
	}
	

	@Override
	public void setConfiguration(XMLConfiguration devicesConfiguration) {
		this.devicesConfig = devicesConfiguration;
		if (devicesConfiguration == null) {
			try {
				devicesConfig = new XMLConfiguration(DEVICE_CONFIGURATION_LOCATION);
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}
}
