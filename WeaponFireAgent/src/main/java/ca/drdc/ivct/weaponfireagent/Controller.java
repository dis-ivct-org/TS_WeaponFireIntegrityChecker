/*
* Copyright (C) Her Majesty the Queen in Right of Canada, as represented by the Minister of National Defence, 2017
*
* Contract:  W7707-145677/001/HAL
*            Call-up 16 
* Author:    OODA Technologies Inc.
* Version:   1.0
* Date:      March 31, 2017
*
*/
 
package ca.drdc.ivct.weaponfireagent;


import ca.drdc.ivct.fom.utils.WeaponFireCSVReader;
import ca.drdc.ivct.fom.warfare.WeaponFire;
import ca.drdc.ivct.weaponfireagent.hlamodule.HlaInterface;
import hla.rti1516e.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ConfigurationException;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;


public class Controller {
	private static Logger logger = LoggerFactory.getLogger(Controller.class);

	private HlaInterface hlaInterface;

	public void execute(WFAgentConfig config) throws ConfigurationException, IOException, ParseException, RTIexception {

		if (config == null) {
			String externalResourceFolder = System.getenv("IVCT_CONF");
			if (externalResourceFolder == null) {
				throw new ConfigurationException("IVCT_CONF is not defined");
			}
			config = new WFAgentConfig(externalResourceFolder + "/IVCTsut/WeaponFireAgent/resources");

		}

		hlaInterface = new HlaInterface();

		try {
			hlaInterface.start(config.getLocalSettingsDesignator(), config.getFom().getAbsolutePath(), config.getFederationName(), config.getFederateName());
		} catch (RTIexception e) {
			logger.error("Could not connect to the RTI using the local settings designator {}", config.getLocalSettingsDesignator(), e);
			throw e;
		}

		// Load all files in testcases folder. This constitutes the federation agreement document (FAD)
		List<WeaponFire> fad = WeaponFireCSVReader.loadCSVFileToWeaponFireList(config.getTestcaseList());

		// Create WeaponFire objects in RTI
		for (WeaponFire weaponFire : fad) {
			try {
				hlaInterface.createWeaponFire(weaponFire);
				logger.info("Created WeaponFire {}", weaponFire);
			} catch (FederateNotExecutionMember | RestoreInProgress | SaveInProgress | NotConnected | RTIinternalError e) {
				logger.error("Error creating a weapon fire", e);
			}
		}
	}

	public void stop() {
		try {
			if (hlaInterface == null) {
				logger.warn("Controller.stop: hlaInterface doesn't exist!");
				return;
			}
			hlaInterface.stop();
		} catch (RTIexception e) {
			logger.error(e.getMessage(), e);
		}

	}
}
