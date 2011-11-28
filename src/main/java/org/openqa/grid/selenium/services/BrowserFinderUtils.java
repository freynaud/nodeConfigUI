package org.openqa.grid.selenium.services;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.exception.GridException;
import org.openqa.selenium.Platform;
import org.openqa.selenium.browserlaunchers.locators.BrowserInstallation;
import org.openqa.selenium.browserlaunchers.locators.Firefox3Locator;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.os.CommandLine;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.opera.core.systems.OperaPaths;

/**
 * helpers to find the default browsers settings.
 * 
 * @author freynaud
 */
public class BrowserFinderUtils {

  private static final Logger log = Logger.getLogger(BrowserFinderUtils.class.getName());

  /**
   * use webdriver internals to guess where firefox is.
   * 
   * @return
   */
  public DesiredCapabilities getDefaultFirefoxInstall() {
    BrowserInstallation install = new Firefox3Locator().findBrowserLocationOrFail();
    DesiredCapabilities c = discoverFirefoxCapability(new File(install.launcherFilePath()));
    return c;
  }

  /**
   * use opera driver internals to guess where opera is.
   * 
   * @return
   */
  public DesiredCapabilities getDefaultOperaInstall() {
    DesiredCapabilities cap = DesiredCapabilities.opera();
    Platform p = Platform.getCurrent();
    cap.setPlatform(p);
    OperaPaths path = new OperaPaths();
    try {
      String s = path.operaPath();
      if (s == null) {
        throw new GridException("opera is not in your path. Is it installed ?");
      }
      cap.setCapability("opera.binary", s);
    } catch (Throwable t) {
      throw new GridException(t.getMessage());
    }
    cap.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
    return cap;
  }

  /**
   * use webdriver internals to guess where chrome is.
   * 
   * @return
   */
  public DesiredCapabilities getDefaultChromeInstall() {
    // check the chrome driver is here.
    ChromeDriverService.createDefaultService();
    // check the chrome itself is installed.
    String c = CommandLine.findExecutable("google-chrome");
    if (c == null) {
      throw new GridException("google-chrome is not in your path. Is it installed ?");
    }
    Platform p = Platform.getCurrent();
    DesiredCapabilities cap = DesiredCapabilities.chrome();
    cap.setPlatform(Platform.getCurrent());
    cap.setCapability("chrome.binary", c);
    cap.setCapability(RegistrationRequest.MAX_INSTANCES, 5);
    return cap;
  }

  public DesiredCapabilities getDefaultIEInstall() {
    if (Platform.getCurrent().is(Platform.WINDOWS)) {
      DesiredCapabilities cap = DesiredCapabilities.internetExplorer();
      cap.setCapability(CapabilityType.PLATFORM, Platform.getCurrent());
      cap.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
      return cap;
    } else {
      throw new GridException("No IE on " + Platform.getCurrent());
    }
  }

  /**
   * guess if the file could be a browser
   * 
   * @param file
   * @return
   */
  public DesiredCapabilities discoverCapability(File file) {
    DesiredCapabilities res;
    try {
      res = discoverFirefoxCapability(file);
      return res;
    } catch (Throwable t) {
      // ignore
    }
    // TODO some more help about the other browsers ?
    if (file.exists() && file.canExecute()) {
      if ("opera".equals(file.getName())) {
        res = DesiredCapabilities.opera();
        res.setCapability("opera.binary", file.getAbsolutePath());
        res.setCapability(CapabilityType.PLATFORM, Platform.getCurrent());
        res.setCapability(RegistrationRequest.MAX_INSTANCES, 5);
      } else if ("google-chrome".equals(file.getName())) {
        res = DesiredCapabilities.chrome();
        res.setCapability("chrome.binary", file.getAbsolutePath());
        res.setCapability(CapabilityType.PLATFORM, Platform.getCurrent());
        res.setCapability(RegistrationRequest.MAX_INSTANCES, 5);
      } else {
        return null;
      }
    } else {
      return null;
    }
    return res;
  }

  /**
   * get all the info possible about this firefox install : OS, version ( from application.ini )
   * 
   * @param exe the firefox executable
   * @return a DesiredCapability with everything filed in
   */
  public DesiredCapabilities discoverFirefoxCapability(File exe) {
    DesiredCapabilities ff = DesiredCapabilities.firefox();
    ff.setCapability(RegistrationRequest.MAX_INSTANCES, 5);
    if (!exe.exists()) {
      throw new RuntimeException("Cannot find " + exe);
    }

    ff.setCapability(FirefoxDriver.BINARY, exe.getAbsolutePath());
    ff.setCapability(CapabilityType.PLATFORM, Platform.getCurrent());
    String p = exe.getParent();
    File appIni = new File(p, "application.ini");

    if (!appIni.exists()) {
      throw new GridException("corrupted install ? cannot find " + appIni.getAbsolutePath());
    }
    Properties prop = new Properties();
    try {
      prop.load(new FileInputStream(appIni));
      String version = prop.getProperty("Version");
      if (version == null) {
        throw new GridException("corrupted install ? cannot find Version in "
            + appIni.getAbsolutePath());
      }
      ff.setVersion(version);
    } catch (Exception e) {
      throw new GridException("corrupted install ? " + e.getMessage());
    }
    ff.setCapability(RegistrationRequest.MAX_INSTANCES, 5);
    return ff;
  }

  /**
   * iterate all the folder in the folder passed as a param, and return all the folders that could
   * be firefox installs.
   * 
   * @param folder the folder to look into
   * @return a list of folder that could be firefox install folders.
   */
  public List<File> guessInstallsInFolder(File folder) {
    List<File> possibleMatch = new ArrayList<File>();

    if (!folder.isDirectory()) {
      return possibleMatch;
    }
    for (File f : folder.listFiles()) {
      if (f.isDirectory() && f.getName().toLowerCase().contains("firefox")) {
        possibleMatch.add(f);
      }
    }
    return possibleMatch;
  }

  /**
   * Find all the browsers install in the same folder as the one specified. If you specify
   * /home/user/firefox2/firefox
   * 
   * the following will also be found : /home/user/firefox3/firefox /home/user/firefox3.5/firefox
   * /home/user/firefox4/firefox
   * 
   * @param exe the exe of a browser.
   * @return
   */
  public List<DesiredCapabilities> findAllInstallsAround(String exe) {
    List<DesiredCapabilities> res = new ArrayList<DesiredCapabilities>();

    DesiredCapabilities a = discoverCapability(new File(exe));
    if (a != null) {
      res.add(a);
    }

    // try to add the new ones
    File more = new File(exe);
    String exeName = more.getName();
    File folder = new File(more.getParent());
    File parent = new File(folder.getParent());

    List<File> folders = guessInstallsInFolder(parent);
    for (File f : folders) {
      File otherexe = new File(f, exeName);
      DesiredCapabilities c = discoverCapability(otherexe);
      if (c != null) {
        res.add(c);
      }
    }
    return res;
  }

  /**
   * is that really needed ?
   * 
   * @param c
   * @param realCap
   */
  public static void updateGuessedCapability(DesiredCapabilities c, DesiredCapabilities realCap) {
    if (!c.getBrowserName().equals(realCap.getBrowserName())) {
      c.setCapability("valid", false);
      throw new GridException("Error validating the browser. Expected " + c.getBrowserName()
          + " but got " + realCap.getBrowserName());
    }
    if (c.getVersion() == null || "".equals(c.getVersion())) {
      c.setVersion(realCap.getVersion());
    }
  }

}
