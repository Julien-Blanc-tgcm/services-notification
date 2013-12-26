/******************************************************************************
 * Copyright (c) 2013, AllSeen Alliance. All rights reserved.
 *
 *    Permission to use, copy, modify, and/or distribute this software for any
 *    purpose with or without fee is hereby granted, provided that the above
 *    copyright notice and this permission notice appear in all copies.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *    WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *    MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *    ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *    WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *    ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *    OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 ******************************************************************************/

package org.alljoyn.ns.transport.producer;

import java.util.EnumMap;
import java.util.Map;

import org.alljoyn.about.AboutService;
import org.alljoyn.about.AboutServiceImpl;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.Variant;
import org.alljoyn.ns.NotificationMessageType;
import org.alljoyn.ns.NotificationServiceException;
import org.alljoyn.ns.commons.GenericLogger;
import org.alljoyn.ns.commons.NativePlatform;
import org.alljoyn.ns.transport.Transport;
import org.alljoyn.ns.transport.TransportNotificationText;
import org.alljoyn.ns.transport.interfaces.NotificationProducer;
import org.alljoyn.ns.transport.interfaces.NotificationTransport;

/**
 * The class manages NotificationProducer transport logic 
 */
public class SenderTransport {
	private static final String TAG = "ioe" + SenderTransport.class.getSimpleName();

	/**
	 * The reference to the platform dependent object
	 */
	private NativePlatform nativePlatform;
	
	/**
	 * TRUE means to stop sending notification messages
	 */
	private boolean stopSending = false;
	
	/**
	 * Map NotificationMessageType to transport object
	 */
	private Map<NotificationMessageType,TransportChannelObject> transportSenderChannels;
	
	/**
	 * The object implements the {@link NotificationProducer} interface
	 */
	private NotificationProducerImpl notifProducerBusObj;
	
	/**
	 * Prepares the AllJoyn daemon to receive incoming session requests
	 */
	private SenderSessionListener sessionListener;
	
	/**
	 * Constructor
	 * @param nativePlatform The reference to the platform dependent object
	 */
	public SenderTransport(NativePlatform nativePlatform) {
		this.nativePlatform = nativePlatform;
	}

	/**
	 * Starts the service in the Notification Sender mode
	 * @throws NotificationServiceException Is thrown if failed to start the SenderTransport
	 */
	public void startSenderTransport() throws NotificationServiceException {
		
		GenericLogger logger 			= nativePlatform.getNativeLogger();
		BusAttachment busAttachment	    = Transport.getInstance().getBusAttachment();
		
		logger.debug(TAG, "Starting a sender transport");
		
		//Creating transportChannel objects
		transportSenderChannels = new EnumMap<NotificationMessageType, TransportChannelObject>(NotificationMessageType.class);
		try {
			for ( NotificationMessageType messageType : NotificationMessageType.values() ) {
				transportSenderChannels.put(messageType,  new TransportChannelObject(messageType, busAttachment, nativePlatform));
			}
		}
		catch (NotificationServiceException nse) {
			logger.error(TAG, nse.getMessage());
			stopSenderTransport();            // We failed to create a channel, stopSenderTransport to allow recovery
			throw nse;
		}
		
		//Initialize the NotificationProducer BusObject
		notifProducerBusObj = new NotificationProducerImpl(this, nativePlatform);
		notifProducerBusObj.init();
		
		//Create session listener to be ready to handle incoming connections
		sessionListener = new SenderSessionListener(nativePlatform);
		sessionListener.init();
		
		//announce the producer via the About service
		AboutService aboutService = AboutServiceImpl.getInstance();
		if ( !aboutService.isServerRunning() ) {
			logger.error(TAG, "The AboutServer wasn't started, unable to send Announcement signals");
			stopSenderTransport();
	        throw new NotificationServiceException("The AboutServer wasn't started");
		}
		
		for (String objPath : NotificationTransportProducer.getServicePath().values()) {
			aboutService.addObjectDescription(objPath, new String[] {NotificationTransport.IF_NAME});
		}	
		
	    aboutService.announce();              // Announce the NotificationSender related objects
	}//startSenderTransport
	
	/**
	 * SenderTransport cleanups
	 * @param logger
	 */
	public void stopSenderTransport() {
		
		GenericLogger logger 			= nativePlatform.getNativeLogger();
		BusAttachment busAttachment	    = Transport.getInstance().getBusAttachment();
		logger.debug(TAG, "Stopping SenderTransport");
		
		if ( transportSenderChannels != null ) {
			for (NotificationMessageType pr : transportSenderChannels.keySet()) {			
				transportSenderChannels.get(pr).clean(busAttachment);
			}
			
			transportSenderChannels = null;
		}
		
		sessionListener.clean();
		sessionListener = null;
		
		notifProducerBusObj.clean();
		notifProducerBusObj = null;
	}//stopSenderTransport
	
	/**
	 * Called when we need to send a signal
	 * @param version The version of the message signature
	 * @param msgId notification Id the id of the sent signal
	 * @param messageType  The message type of the sent message
	 * @param deviceId Device id
	 * @param deviceName Device name
	 * @param appId App id
	 * @param appName App name
	 * @param attributes All the notification metadata
	 * @param customAttributes The customAttributes
	 * @param text Array of texts to be sent
	 * @param ttl Notification message TTL 
	 * @throws NotificationServiceException
	 */
	public void sendNotification(int version, int msgId, NotificationMessageType messageType, String deviceId, String deviceName, byte[] appId, String appName, Map<Integer, Variant> attributes, Map<String, String> customAttributes,TransportNotificationText[] text, int ttl) throws NotificationServiceException {
		
		GenericLogger logger = nativePlatform.getNativeLogger();
		
		if (stopSending){
			logger.debug(TAG, "In stopSending mode NOT sending notification!!!");
			return;
		}
		
		TransportChannelObject senderChannel = transportSenderChannels.get(messageType);
		if ( senderChannel == null ) {
			throw new NotificationServiceException("Received an unknown message type: '" + messageType + "', can't find a transport channel to send the notification");
		}
		
		//send this notification to the correct object based on messageType
		senderChannel.sendNotification(version,
									      msgId,																	
									      messageType.getTypeId(),
										  deviceId,
										  deviceName,
										  appId,
										  appName,												
										  attributes,
										  customAttributes,
										  text,
										  ttl);
	}//sendNotification
	
	/**
	 * Cancel the last message sent for the given messageType
	 * @param messageType
	 * @throws NotificationServiceException 
	 */
	public void deleteLastMessage(NotificationMessageType messageType) throws NotificationServiceException {

		TransportChannelObject senderChannel = transportSenderChannels.get(messageType);
		if ( senderChannel == null ) {
			throw new NotificationServiceException("Received an unknown message type: '" + messageType + "', can't find a transport channel to delete the notification");
		}
		
  		//delete the last message of this messageType
		senderChannel.deleteNotification();
	}//deleteLastMessage
	
	/**
	 * @param notifId The notification id of the notification message to be deleted
	 */
	public void acknowledge(int notifId) {
		
		GenericLogger logger = nativePlatform.getNativeLogger();
		logger.debug(TAG, "Acknowledgement method has been called for notifId: '" + notifId + "', searching for the relevant object");
		
		boolean isObjFound = false;
		
		for ( TransportChannelObject channelObj : transportSenderChannels.values() ) {
			
			channelObj.acquireLock();       // Lock the object to prevent changing its state
			
			Integer chanObjNotifId = channelObj.getNotificationId(); 
			
			if ( chanObjNotifId != null && chanObjNotifId == notifId ) {     // Found the object to be cancelled
				logger.debug(TAG, "Found the object with notifId: '" + notifId + "' to be cancelled");
				channelObj.deleteNotification();
				isObjFound = true;
				channelObj.releaseLock();   // release the lock
				break;
			}
			else {
				channelObj.releaseLock();   // release the lock and continue iterating
			}
			
		}//for
		
		if ( !isObjFound ) {
			logger.debug(TAG, "Failed to find the object with the notifId: '" + notifId + "'");
		}
	}//acknowledge
	
	/**
	 * Acknowledge the notification and then send the dismiss session-less-signal 
	 * @param notifId
	 */
	public void dismiss(int notifId) {
		GenericLogger logger = nativePlatform.getNativeLogger();
		logger.debug(TAG, "Dismiss method has been called, first call for Acknowledgement for notifId: '" + notifId + "'");
		acknowledge(notifId);
		//TODO Call for Dismisser
	}//dismiss
}