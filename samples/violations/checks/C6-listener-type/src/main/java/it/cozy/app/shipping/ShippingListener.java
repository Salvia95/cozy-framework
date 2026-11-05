package it.cozy.app.shipping;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import it.cozy.app.order.OrderPlainEvent;

@Component
class ShippingListener {

	@ApplicationModuleListener
	void on(OrderPlainEvent event) {
	}
}
