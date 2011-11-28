package org.openqa.grid.selenium;

public enum Icon {
	LOAD("/extra/resources/loader.gif"),

	ALERT("/extra/resources/alert.png"),

	CLEAN("/extra/resources/clean.png"),
	
	DELETE("/extra/resources/edit_remove.png"),

	NOT_SURE("/extra/resources/kblackbox.png"),

	VALIDATED("/extra/resources/cnrgrey.png"),

	VALIDATE("/extra/resources/cnrclient.png"),

	FIREFOX("/extra/resources/firefox.png"),

	CHROME("/extra/resources/chrome.png"),

	AURORA("/extra/resources/aurora.png"),

	MAC("/extra/resources/mac.png"),
	
	WIN("/extra/resources/win.jpg"),

	LINUX("/extra/resources/tux.png");

	private final String path;

	Icon(String path) {
		this.path = path;
	}

	public String path() {
		return path;
	}

}