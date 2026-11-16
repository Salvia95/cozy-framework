package it.cozy.app.shipping.internal.app.command;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import it.cozy.app.order.spi.dto.OrderPlainEvent;

@Component
class ShippingListener {

	@ApplicationModuleListener
	void on(OrderPlainEvent event) {
	}
}
