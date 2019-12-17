/*******************************************************************************
 * Copyright (C) Her Majesty the Queen in Right of Canada, 
 * as represented by the Minister of National Defence, 2018
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package ca.drdc.ivct.tc_wf_integritycheck;

import ca.drdc.ivct.fom.utils.WeaponFireCSVReader;
import ca.drdc.ivct.fom.warfare.WeaponFire;
import ca.drdc.ivct.tc_lib_wf_integritycheck.CountdownTimer;
import ca.drdc.ivct.tc_lib_wf_integritycheck.IntegrityCheckTcParam;
import ca.drdc.ivct.tc_lib_wf_integritycheck.WeaponFireIntegrityCheckBaseModel;
import de.fraunhofer.iosb.tc_lib.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

public class WeaponFireIntegrityTC_0001 extends AbstractTestCase {

    private static final String FEDERATE_NAME = "WeaponFireTester";
    private IntegrityCheckTcParam tcParam;
    private IVCT_RTIambassador ivctRtiAmbassador;
    private IVCT_LoggingFederateAmbassador loggingFedAmbassador;
    private WeaponFireIntegrityCheckBaseModel weaponFireDataModel;
    private List<WeaponFire> fad;

    @Override
    protected IVCT_BaseModel getIVCT_BaseModel(String tcParamJson, Logger logger) throws TcInconclusive {
        tcParam = new IntegrityCheckTcParam(tcParamJson);
        ivctRtiAmbassador = IVCT_RTI_Factory.getIVCT_RTI(logger);
        weaponFireDataModel = new WeaponFireIntegrityCheckBaseModel(logger, ivctRtiAmbassador, tcParam);
        loggingFedAmbassador = new IVCT_LoggingFederateAmbassador( weaponFireDataModel, logger);

        return weaponFireDataModel;
    }

    @Override
    protected void logTestPurpose(Logger logger) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        stringBuilder.append("---------------------------------------------------------------------\n");
        stringBuilder.append("TEST PURPOSE\n");
        stringBuilder.append("Tests if a SuT federate creates Weapon Fire Interactions with Identifiers\n");
        stringBuilder.append("that match the ones defined in the federation agreement document (FAD). Then the \n");
        stringBuilder.append("parameters are checked and finally the spatial parameters.\n");
        stringBuilder.append("The FAD is publised as a csv document containing listings of Identifiers \n");
        stringBuilder.append("and interaction parameters.\n");
        stringBuilder.append("---------------------------------------------------------------------\n");
        stringBuilder.append("TC_0001 focus is to check the interactions' identifiers match.\n");
        stringBuilder.append("---------------------------------------------------------------------\n");
        String testPurpose = stringBuilder.toString();

        logger.info(testPurpose);
    }

    @Override
    protected void preambleAction(Logger logger) throws TcInconclusive {
        logger.info("Attempting to connect to RTI with federate: {}", FEDERATE_NAME);
        // Initiate RTI
        weaponFireDataModel.initiateRti(FEDERATE_NAME, loggingFedAmbassador);

        // Get handles and publish / subscribe interactions
        if (weaponFireDataModel.init()) {
            throw new TcInconclusive("weapaonFireDataModel.init() failed to execute");
        }

        // Load all files in test cases folder. This constitutes the federation agreement document (FAD)
        fad = WeaponFireCSVReader.loadCSVFileToWeaponFireList(Arrays.asList(tcParam.getFadUrl()));

        if (fad.isEmpty()) {
            throw new TcInconclusive("The FAD is empty.");
        }


        // Let five second to IVCT federation client to discover the weaponfires.
        new CountdownTimer(tcParam.getWaitingPeriod(), logger).run();
    }

    /**
     * Tests discovered BaseEntity objects by comparing them with the ones in
     * the FAD.
     * 
     * @throws TcInconclusive due to connection errors or csv
     * @throws TcFailed due to entities not being the same
     */
    @Override
    protected void performTest(Logger logger) throws TcInconclusive, TcFailed {
        logger.info("Welcome to the WeaponFireTester Federate of the IVCT Federation");
        logger.info("Make sure that the Weapon Fire Agent federate has joined the federation!");

        weaponFireDataModel.testWeaponFireIntegrityIdentity(fad);
        weaponFireDataModel.testWeaponFireIntegrityParameters(fad);
        weaponFireDataModel.testWeaponFireIntegritySpatial(fad);
    }

    @Override
    protected void postambleAction(Logger logger) throws TcInconclusive {
        weaponFireDataModel.terminateRti();
        weaponFireDataModel = new WeaponFireIntegrityCheckBaseModel(logger, ivctRtiAmbassador, tcParam);
    }

}
