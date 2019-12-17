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

import ca.drdc.ivct.fom.base.structs.EventIdentifierStruct;
import ca.drdc.ivct.fom.utils.WeaponFireEqualUtils;
import ca.drdc.ivct.fom.warfare.WeaponFire;
import ca.drdc.ivct.fom.utils.WeaponFireCSVReader;
import ca.drdc.ivct.tc_lib_wf_integritycheck.CountdownTimer;
import de.fraunhofer.iosb.tc_lib.TcFailed;
import de.fraunhofer.iosb.tc_lib.TcInconclusive;
import de.fraunhofer.iosb.tc_lib.converter.DisModelConverter;
import de.fraunhofer.iosb.tc_lib.dis.DISAbstractTestCase;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.Optional;

/**
 *
 */
public class DisWeaponFireIntegrityTC_0001 extends DISAbstractTestCase {

    private List<WeaponFire> fad ;
    private String lineSeparator = "\n---------------------------------------------------------------------\n";
    private Map<String, Double> spatialThresold;

    @Override
    protected void logTestPurpose(Logger logger) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(lineSeparator);
        stringBuilder.append("TEST PURPOSE\n");
        stringBuilder.append("Tests if a SuT federate creates Weapon Fire Interactions with Identifiers\n");
        stringBuilder.append("that match the ones defined in the federation agreement document (FAD). \n");
        stringBuilder.append("The FAD is publised as a csv document containing listings of Identifiers \n");
        stringBuilder.append("and interaction parameters.");
        stringBuilder.append(lineSeparator);
        stringBuilder.append("TC_0001 focus is to check the interactions' identifiers match.");
        stringBuilder.append(lineSeparator);
        String testPurpose = stringBuilder.toString();

        logger.info(testPurpose);
    }

    @Override
    protected void preambleAction(Logger logger) throws TcInconclusive {

        this.spatialThresold = this.param.getSpatialValueThreshold();

        // Load all files in test cases folder. This constitutes the federation agreement document (FAD)
        this.fad = WeaponFireCSVReader.loadCSVFileToWeaponFireList(super.param.getFadUrls());
        if (fad.isEmpty()) {
            throw new TcInconclusive("The FAD is empty.");
        }

        logger.info("Send weapon fire interaction to this DIS federate. You have 10 seconds");

        // Let five second to IVCT federation client to discover the weaponfires.
        new CountdownTimer(super.param.getWaitingPeriod(), logger).run();
    }

    /**
     * Tests discovered Weapon Fire objects by comparing them with the ones in the FAD.
     * 
     * @throws TcInconclusive due to connection errors or csv
     * @throws TcFailed due to entities not being the same
     */
    @Override
    protected void performTest(Logger logger) throws TcInconclusive, TcFailed {
        logger.info("Welcome to the DisWeaponFireTester Federate of the IVCT Federation");

        // Gather received entities
        Map<EventIdentifierStruct, WeaponFire> receivedWeaponFireWithoutDuplicate = new HashMap<>();
        super.disManager.getReceivedFirePdu().stream()
                .map(DisModelConverter::disWeaponFireToRpr)
                .forEachOrdered(weaponFire -> receivedWeaponFireWithoutDuplicate.computeIfAbsent(weaponFire.getEventIdentifier(), (key)->weaponFire));

        logger.info("Executing Test");

        if (receivedWeaponFireWithoutDuplicate.isEmpty()) {
            throw new TcInconclusive("No WeaponFire interactions found on the RTI bus. A system "
                    + "under test must create discoverable WeaponFire interactions before attempting the test.");
        }
        // Traverse the discovered WeaponFire interactions.
        boolean testPassed = true;
        boolean fadWeaponFirePassesTest;
        StringBuilder failedStringBuilder = new StringBuilder();
        StringBuilder debugStringBuilder = new StringBuilder();
        if (fad.size() != receivedWeaponFireWithoutDuplicate.size()) {
            testPassed = false;
            String failedMessage = "FAIL: Fad and discovered WeaponFire sizes do not match "+fad.size()+" | "+ receivedWeaponFireWithoutDuplicate.size();
            failedStringBuilder.append(failedMessage);
            logger.info(failedMessage);
        } else {
            debugStringBuilder.append("Received the good amount of WeaponFire according to the fad");
        }

        for (WeaponFire fadWeaponFire : fad) {
            // Loop over each discovered weapon fire to check if this fadWeaponFire is present

            Optional<WeaponFire> optionalWeaponFire =
                    receivedWeaponFireWithoutDuplicate.values()
                        .stream()
                        .filter(discoveredWeaponFire -> discoveredWeaponFire.getEventIdentifier().equals(fadWeaponFire.getEventIdentifier()))
                        .findFirst();

            String failedMessage;

            if (!optionalWeaponFire.isPresent()) {
                testPassed = false;
                failedMessage = "FAIL: WeaponFire Interaction from FAD with identifier " + fadWeaponFire
                        + " found no weaponFire match in discovered Weapon Fire Interactions : "+optionalWeaponFire.get();
                failedStringBuilder.append("\n"+failedMessage);
            } else {
                debugStringBuilder.append("OKAY: WeaponFire Interaction from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                        + " was found in discovered Weapon Fire Interactions");

                WeaponFireEqualUtils weaponFireEqualUtils = new WeaponFireEqualUtils(spatialThresold);
                fadWeaponFirePassesTest = weaponFireEqualUtils.areWeaponFiresParametersEquals(fadWeaponFire, optionalWeaponFire.get());
                if (!fadWeaponFirePassesTest) {
                    testPassed = false;
                    failedMessage = "FAIL: WeaponFire Interaction from FAD with identifier " + fadWeaponFire
                            + " has not the same parameters as its discovered Weapon Fire match : "+optionalWeaponFire.get();

                    failedStringBuilder.append("\n"+failedMessage);
                } else {
                    debugStringBuilder.append("OKAY: WeaponFire from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                            + " found an ID and type match in discovered WeaponFires");
                }

                boolean fadWeaponFireLocationPassesTest = weaponFireEqualUtils.worldLocationEquals(
                        fadWeaponFire.getFiringLocation(),
                        optionalWeaponFire.get().getFiringLocation());

                boolean fadWeaponFireVelocityPassesTest = weaponFireEqualUtils.velocityEquals(
                        fadWeaponFire.getInitialVelocityVector(),
                        optionalWeaponFire.get().getInitialVelocityVector());

                if (!(fadWeaponFireLocationPassesTest && fadWeaponFireVelocityPassesTest) ){
                    testPassed = false;
                    failedMessage = "FAIL: WeaponFire from FAD with identifier " + fadWeaponFire
                            + " has not the same spatial info as its discovered WeaponFire : "+optionalWeaponFire.get();
                    failedStringBuilder.append("\n"+failedMessage + lineSeparator);
                } else {
                    debugStringBuilder.append("OKAY: WeaponFire from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                            + " found an ID and spatial match in discovered WeaponFires");
                }
            }
        }

        logger.debug(debugStringBuilder.toString());
        if (!testPassed) {
            throw new TcFailed("Test failed due to errors in Weapon Fire Interaction(s) or absent/unrecognized Weapon Fire Interaction(s) : "+failedStringBuilder.toString()) ;
        } else {
            logger.info("{} TEST IS COMPLETED SUCCESFULLY. {}",lineSeparator,lineSeparator);
        }
    }

    @Override
    protected void postambleAction(Logger logger) throws TcInconclusive {

    }

}
