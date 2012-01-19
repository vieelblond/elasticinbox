/**
 * Copyright (c) 2011 Optimax Software Ltd
 * 
 * This file is part of ElasticInbox.
 * 
 * ElasticInbox is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 2 of the License, or (at your option) any later
 * version.
 * 
 * ElasticInbox is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * ElasticInbox. If not, see <http://www.gnu.org/licenses/>.
 */

package com.elasticinbox.lmtp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.james.protocols.api.logger.Logger;
import org.apache.james.protocols.lmtp.LMTPProtocolHandlerChain;
import org.apache.james.protocols.netty.NettyServer;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.protocols.smtp.MailAddress;

import com.elasticinbox.config.Configurator;
import com.elasticinbox.lmtp.delivery.IDeliveryAgent;
import com.elasticinbox.lmtp.server.LMTPServerConfig;
import com.elasticinbox.lmtp.server.api.DeliveryReturnCode;
import com.elasticinbox.lmtp.server.api.handler.ElasticInboxDeliveryHandler;
import com.elasticinbox.lmtp.utils.JamesProtocolsLogger;

/**
 * LMTP proxy main class which sends traffic to multiple registered handlers
 * 
 * @author Rustam Aliyev
 */
public class LMTPProxyServer
{
	private NettyServer server;
	private IDeliveryAgent backend;

	protected LMTPProxyServer(IDeliveryAgent backend) {
	    this.backend = backend;
	}

	public void start() throws Exception
	{
		Logger logger = new JamesProtocolsLogger();

		LMTPProtocolHandlerChain chain = new LMTPProtocolHandlerChain();
		chain.add(0, new ElasticInboxDeliveryHandler(backend));
		chain.wireExtensibleHandlers();

		server = new NettyServer(new SMTPProtocol(chain, new LMTPServerConfig(), logger));
		server.setListenAddresses(new InetSocketAddress(Configurator.getLmtpPort()));
		server.setMaxConcurrentConnections(Configurator.getLmtpMaxConnections());
		server.setTimeout(LMTPServerConfig.CONNECTION_TIMEOUT);
		server.setUseExecutionHandler(true, 16);
		server.bind();
	}

	public void stop() {
		server.unbind();
	}

	public static void main(String[] args) throws Exception
	{
		new LMTPProxyServer(new IDeliveryAgent() {

			@Override
			public Map<MailAddress, DeliveryReturnCode> deliver(MailEnvelope env) throws IOException
			{
				Map<MailAddress, DeliveryReturnCode> map = new HashMap<MailAddress, DeliveryReturnCode>();
				for (MailAddress address : env.getRecipients()) {
					map.put(address, DeliveryReturnCode.OK);
				}
				return map;
			}

		}).start();
	}
}
