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

#ifndef NOTIFICATIONTRANSPORTSUPERAGENT_H_
#define NOTIFICATIONTRANSPORTSUPERAGENT_H_

#include "NotificationTransport.h"

namespace ajn {
namespace services {

/**
 * Notification Transport SuperAgent. Used to Create the SuperAgent Interface
 * on the receiver side and register a signal handler to receive the notify signals
 */
class NotificationTransportSuperAgent : public NotificationTransport {
  public:

    /**
     * Constructor for TransportSuperAgent
     * @param bus         - BusAttachment that is used
     * @param servicePath - servicePath of BusObject
     * @param status      - success/failure
     */
    NotificationTransportSuperAgent(ajn::BusAttachment* bus,
                                    qcc::String const& servicePath, QStatus& status, bool isFirstSuperAgent = true);

    /**
     * Destructor of TransportSuperAgent
     */
    ~NotificationTransportSuperAgent() { };

    /**
     * Callback when Signal arrives
     * @param member    Method or signal interface member entry.
     * @param srcPath   Object path of signal emitter.
     * @param message   The received message.
     */
    void handleSignal(const ajn::InterfaceDescription::Member* member,
                      const char* srcPath, ajn::Message& msg);

    void unregisterHandler(ajn::BusAttachment* bus);

    void setIsFirstSuperAgent(bool isFirstSuperAgent);

  private:

    /**
     * Boolean to indicate if this is the first time the SuperAgent called back
     */
    bool m_IsFirstSuperAgent;
};
} //namespace services
} //namespace ajn

#endif /* NOTIFICATIONTRANSPORTSUPERAGENT_H_ */
