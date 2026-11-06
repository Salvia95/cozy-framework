package it.cozy.app.a.internal.app.command;

import it.cozy.app.b.internal.infra.BHelper;

class AServiceImpl implements AService {

	@Override
	public String run() {
		return new BHelper().help();
	}
}
