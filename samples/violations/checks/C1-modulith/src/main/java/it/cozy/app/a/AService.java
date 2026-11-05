package it.cozy.app.a;

import it.cozy.app.b.internal.BHelper;

public class AService {

	public String run() {
		return new BHelper().help();
	}
}
