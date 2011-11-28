package org.openqa.grid.selenium;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.grid.common.JSONConfigurationUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.exception.GridException;
import org.openqa.grid.selenium.services.BrowserFinderUtils;
import org.openqa.grid.selenium.services.HubUtils;
import org.openqa.selenium.Platform;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.remote.DesiredCapabilities;

public class Node {

	// node description
	private List<DesiredCapabilities> capabilities = new ArrayList<DesiredCapabilities>();
	private Map<String, Object> configuration = new HashMap<String, Object>();

	// helpers
	private BrowserFinderUtils finder = new BrowserFinderUtils();
	private Map<String, String> errorPerBrowser = new HashMap<String, String>();
	private final int port;
	private File backup = new File("node.json");
	private final String ip;

	public Node(int port) {
		this.port = port;
		ip = new NetworkUtils().getIp4NonLoopbackAddressOfThisMachine().getHostAddress();
		loadDefault();
	}

	/**
	 * loads the default node settings for the current platform. Doesn't
	 * validate much.
	 */
	public void loadDefault() {
		clearAll();

		try {
			setHubURL(new URL("http://localhost:4444"));
		} catch (MalformedURLException e1) {
			// shouldnt happen
			throw new RuntimeException("impossible");
		}

		configuration.put(RegistrationRequest.REMOTE_HOST, getRemoteURL());
		configuration.put(RegistrationRequest.AUTO_REGISTER, false);
		configuration.put(RegistrationRequest.CLEAN_UP_CYCLE, 5000);
		configuration.put(RegistrationRequest.TIME_OUT, 30000);
		configuration.put(RegistrationRequest.MAX_SESSION, 5);
		configuration.put(RegistrationRequest.PROXY_CLASS, org.openqa.grid.selenium.proxy.WebDriverRemoteProxy.class.getCanonicalName());

		try {
			capabilities.add(finder.getDefaultIEInstall());
		} catch (Throwable e) {
			errorPerBrowser.put("internet eplorer", e.getMessage());
		}
		try {
			capabilities.add(finder.getDefaultFirefoxInstall());
		} catch (Throwable e) {
			errorPerBrowser.put("firefox", e.getMessage());
		}
		try {
			capabilities.add(finder.getDefaultChromeInstall());
		} catch (Throwable e) {
			errorPerBrowser.put("chrome", e.getMessage());
		}
		try {
			capabilities.add(finder.getDefaultOperaInstall());
		} catch (Throwable e) {
			errorPerBrowser.put("opera", e.getMessage());
		}

	}

	private String getRemoteURL() {
		return "http://" + ip + ":" + getPort() + "/wb/hub";
	}

	private void clearAll() {
		capabilities.clear();
		configuration.clear();
		errorPerBrowser.clear();
	}

	/**
	 * get the location of the json config file describing this node. See also
	 * {@link Node#load()} and {@link Node#save()}
	 * 
	 * @return
	 */
	public File getBackupFile() {
		return backup;
	}

	/**
	 * set the location of the config file describing the node.
	 * 
	 * @param backup
	 */
	public void setBackupFile(File backup) {
		this.backup = backup;
	}

	/**
	 * Loads a node description from the backup file.
	 * 
	 * @throws IOException
	 * @throws JSONException
	 */
	public void load() throws IOException, JSONException {
		clearAll();

		JSONObject object = JSONConfigurationUtils.loadJSON(backup.getCanonicalPath());
		JSONArray caps = object.getJSONArray("capabilities");

		for (int i = 0; i < caps.length(); i++) {
			DesiredCapabilities c = new DesiredCapabilities();
			JSONObject cap = caps.getJSONObject(i);
			for (Iterator iterator = cap.keys(); iterator.hasNext();) {
				String key = (String) iterator.next();
				c.setCapability(key, cap.get(key));
			}
			capabilities.add(c);
		}

		JSONObject conf = object.getJSONObject("configuration");
		for (Iterator iterator = conf.keys(); iterator.hasNext();) {
			String key = (String) iterator.next();
			configuration.put(key, conf.get(key));
		}
	}

	/**
	 * saves the current node description in the associated json file.
	 * 
	 * @throws IOException
	 */
	public void save() throws IOException {
		JSONObject node = getJSON();
		if (backup == null) {
			throw new RuntimeException("Cannot save the config. File not specified.");
		}
		BufferedWriter out = new BufferedWriter(new FileWriter(backup.getAbsolutePath()));
		out.write(node.toString());
		out.close();

	}

	/**
	 * get the JSON object representing the node. That's the object grid expect
	 * in a registration request.
	 * 
	 * @return
	 */
	public JSONObject getJSON() {
		try {
			JSONObject res = new JSONObject();

			// capabilities
			JSONArray caps = new JSONArray();
			for (DesiredCapabilities cap : capabilities) {
				JSONObject c = new JSONObject();
				for (String key : cap.asMap().keySet()) {
					c.put(key, cap.getCapability(key));
				}
				caps.put(c);
			}

			// configuration
			JSONObject c = new JSONObject();
			for (String key : configuration.keySet()) {
				c.put(key, configuration.get(key));
			}
			c.put("hub", getHubURL());

			res.put("capabilities", caps);
			res.put("configuration", c);
			return res;
		} catch (JSONException e) {
			throw new RuntimeException("Bug. " + e.getMessage());
		}
	}

	public List<DesiredCapabilities> getCapabilities() {
		return capabilities;
	}

	public Map<String, Object> getConfiguration() {
		return configuration;
	}

	/**
	 * add a new browser install to the node. Validate that it's not a dup.
	 * browsers are equals when their binary is.
	 * 
	 * @param cap
	 * @return
	 */
	public boolean addNewBrowserInstall(DesiredCapabilities cap) {
		// 2 firefox installs are equal if the point to the same exe.
		String key = null;
		String browser = cap.getBrowserName();
		if ("firefox".equals(browser)) {
			key = FirefoxDriver.BINARY;
		} else if ("chrome".equals(browser)) {
			key = "chrome.binary";
		} else if ("opera".equals(browser)) {
			key = "opera.binary";
		} else {
			throw new RuntimeException("NI");
		}
		String newOne = (String) cap.getCapability(key);

		for (DesiredCapabilities c : getCapabilities()) {
			String path = (String) c.getCapability(key);
			if (path != null && path.equalsIgnoreCase(newOne)) {
				return false;
			}
		}
		capabilities.add(cap);
		return true;
	}

	public Map<String, String> getErrorPerBrowser() {
		return errorPerBrowser;
	}

	public int getPort() {
		return port;
	}

	public void setHubURL(URL hubUrl) {
		configuration.put("hub", hubUrl);
	}

	public URL getHubURL() {
		try {
			return new URL(configuration.get("hub").toString());
		} catch (MalformedURLException e) {
			throw new GridException(configuration.get("hub") + " is not a valid URL");
		}
	}

	public Platform getPlatform() {
		return Platform.getCurrent();
	}

	public boolean register() throws IOException, JSONException {
		DefaultHttpClient client = new DefaultHttpClient();

		BasicHttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest("POST", getHubURL().toExternalForm() + "/grid/register");
		r.setEntity(new StringEntity(getJSON().toString()));

		HttpHost host = new HttpHost(getHubURL().getHost(), getHubURL().getPort());
		HttpResponse response = client.execute(host, r);

		return response.getStatusLine().getStatusCode() == 200;
	}

	public JSONObject getStatusFromHub() {
		HubUtils hubUtils = new HubUtils(getHubURL().getHost(), getHubURL().getPort());
		return hubUtils.getProxyDetails(getRemoteURL());
	}
}
