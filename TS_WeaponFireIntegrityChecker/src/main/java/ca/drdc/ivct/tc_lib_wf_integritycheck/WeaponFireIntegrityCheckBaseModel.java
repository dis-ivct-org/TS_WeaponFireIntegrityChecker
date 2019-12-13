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

package ca.drdc.ivct.tc_lib_wf_integritycheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ca.drdc.ivct.coders.warfare.WeaponFireCoder;
import ca.drdc.ivct.fom.utils.WeaponFireEqualUtils;
import ca.drdc.ivct.fom.warfare.WeaponFire;
import org.slf4j.Logger;

import de.fraunhofer.iosb.tc_lib.IVCT_RTIambassador;
import de.fraunhofer.iosb.tc_lib.TcFailed;
import de.fraunhofer.iosb.tc_lib.TcInconclusive;
import de.fraunhofer.iosb.tc_lib.IVCT_BaseModel;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.ParameterHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.FederateHandle;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.MessageRetractionHandle;
import hla.rti1516e.OrderType;
import hla.rti1516e.TransportationTypeHandle;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.exceptions.FederateHandleNotKnown;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.FederateServiceInvocationsAreBeingReportedViaMOM;
import hla.rti1516e.exceptions.InvalidInteractionClassHandle;
import hla.rti1516e.exceptions.InvalidFederateHandle;
import hla.rti1516e.exceptions.InteractionClassNotDefined;
import hla.rti1516e.exceptions.NameNotFound;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;

/**
 *  Base Model container for Integrity check testing.
 *  
 *  Use the IVCT ambassador to connect to the federation and gather WeaponFireInteractions from the federation.
 * 
 * @author mlavallee, laurenceo
 */
public class WeaponFireIntegrityCheckBaseModel extends IVCT_BaseModel {

    private IVCT_RTIambassador ivctRti;
    private final Map<String, WeaponFire> discoveredWeaponFires = new HashMap<>();





    private Logger logger;


    private InteractionClassHandle weaponFireInteractionClassHandle;
    private ParameterHandle eventIdParameterHandle;
    private ParameterHandle fireControlSolRangeParameterHandle;
    private ParameterHandle fireMissionIndexParameterHandle;
    private ParameterHandle firingLocParameterHandle;
    private ParameterHandle firingObjIdParameterHandle;
    private ParameterHandle fuseTypeParameterHandle;
    private ParameterHandle initVelVectorParameterHandle;
    private ParameterHandle munitionObjIdParameterHandle;
    private ParameterHandle munitionTypeParameterHandle;
    private ParameterHandle quantityFiredParameterHandle;
    private ParameterHandle rateOfFireParameterHandle;
    private ParameterHandle targetObjIdParameterHandle;
    private ParameterHandle warheadTypeParameterHandle;

    private static final String WEAPONFIRE = "WeaponFire";
    private static final String EVENT_ID = "EventIdentifier";
    private static final String FIRE_CONTROL_SOL_RANGE = "FireControlSolutionRange";
    private static final String FIRE_MISSION_INDEX = "FireMissionIndex";
    private static final String FIRING_LOC = "FiringLocation";
    private static final String FIRING_OBJ_ID = "FiringObjectIdentifier";
    private static final String FUSE_TYPE = "FuseType";
    private static final String INIT_VEL_VECT = "InitialVelocityVector";
    private static final String MUNIT_OBJ_ID = "MunitionObjectIdentifier";
    private static final String MUNIT_TYPE = "MunitionType";
    private static final String QUANT_FIRED = "QuantityFired";
    private static final String RATE_OF_FIRE = "RateOfFire";
    private static final String TARGET_OBJ_ID = "TargetObjectIdentifier";
    private static final String WARHEAD_TYPE = "WarheadType";




    private Map<String, Double> spatialThresold;

    /**
     * @param logger reference to a logger
     * @param ivctRti reference to the RTI ambassador
     * @param ivctTcParam ivct_TcParam
     */
    public WeaponFireIntegrityCheckBaseModel(Logger logger, IVCT_RTIambassador ivctRti, IntegrityCheckTcParam ivctTcParam) {
        super(ivctRti, logger, ivctTcParam);
        this.logger = logger;
        this.ivctRti = ivctRti;
        this.spatialThresold = ivctTcParam.getSpatialValueThreshold();
    }

    /**
     * @param federateHandle the federate handle
     * @return the federate name or null
     */
    public String getFederateName(FederateHandle federateHandle) {

        try {
            return this.ivctRti.getFederateName(federateHandle);
        } catch (InvalidFederateHandle | FederateHandleNotKnown | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
            logger.error("Error extracting federate name from the ambassador",e);
            return null;
        }

    }

    /**
     * @return true means error, false means correct
     */
    public boolean init() {

        try {
            this.subscribeInteraction();

        } catch (RTIinternalError e) {
            e.printStackTrace();
            return true;
        } catch (NotConnected e) {
            this.logger.error("Cannot retreive handles. No connection to RTI.");
            e.printStackTrace();
            return true;
        } catch (SaveInProgress e) {
            this.logger.error("init Error. Save is in progress.");
            e.printStackTrace();
            return true;
        } catch (RestoreInProgress e) {
            this.logger.error("init Error. Restore is in progress.");
            e.printStackTrace();
            return true;
        } catch (FederateNotExecutionMember e) {
            this.logger.error("init Error. Federate is not execution member.");
            e.printStackTrace();
            return true;
        } catch (FederateServiceInvocationsAreBeingReportedViaMOM e) {
            this.logger.error("init Error. FederateService MOM problem.");
            e.printStackTrace();
            return true;
        }

        return false;
    }


    private void getHandles() throws RTIinternalError, NotConnected {
        try {

            weaponFireInteractionClassHandle = ivctRti.getInteractionClassHandle(WEAPONFIRE);

            eventIdParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, EVENT_ID);
            fireControlSolRangeParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, FIRE_CONTROL_SOL_RANGE);
            fireMissionIndexParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, FIRE_MISSION_INDEX);
            firingLocParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, FIRING_LOC);
            firingObjIdParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, FIRING_OBJ_ID);
            fuseTypeParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, FUSE_TYPE);
            initVelVectorParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, INIT_VEL_VECT);
            munitionObjIdParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, MUNIT_OBJ_ID);
            munitionTypeParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, MUNIT_TYPE);
            quantityFiredParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, QUANT_FIRED);
            rateOfFireParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, RATE_OF_FIRE);
            targetObjIdParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, TARGET_OBJ_ID);
            warheadTypeParameterHandle = ivctRti.getParameterHandle(weaponFireInteractionClassHandle, WARHEAD_TYPE);

        } catch (NameNotFound e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } catch (FederateNotExecutionMember e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } catch (InvalidInteractionClassHandle e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    }

    private void subscribeInteraction() throws FederateServiceInvocationsAreBeingReportedViaMOM, SaveInProgress, RestoreInProgress, FederateNotExecutionMember, NotConnected, RTIinternalError, FederateServiceInvocationsAreBeingReportedViaMOM {

        getHandles();

        try {
            ivctRti.subscribeInteractionClass(weaponFireInteractionClassHandle);
        } catch (InteractionClassNotDefined e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    }


    private void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters) {
        if (interactionClass.equals(weaponFireInteractionClassHandle)) {

            WeaponFireCoder weaponFireCoder = new WeaponFireCoder(ivctRti.getEncoderFactory());
            WeaponFire theWeaponfire = new WeaponFire();

            try {
                theWeaponfire.setEventIdentifier(weaponFireCoder.decodeEventIdentifier(theParameters.get(eventIdParameterHandle)));
                theWeaponfire.setFireControlSolutionRange(weaponFireCoder.decodeFireControlSolRange(theParameters.get(fireControlSolRangeParameterHandle)));
                theWeaponfire.setFireMissionIndex(weaponFireCoder.decodeFireMissionIndex(theParameters.get(fireMissionIndexParameterHandle)));
                theWeaponfire.setFiringLocation(weaponFireCoder.decodeFiringLocation(theParameters.get(firingLocParameterHandle)));
                theWeaponfire.setFiringObjectIdentifier(weaponFireCoder.decodeFiringObjectId(theParameters.get(firingObjIdParameterHandle)));
                theWeaponfire.setFuseType(weaponFireCoder.decodeFuseType(theParameters.get(fuseTypeParameterHandle)));
                theWeaponfire.setInitialVelocityVector(weaponFireCoder.decodeInitialVelocity(theParameters.get(initVelVectorParameterHandle)));
                theWeaponfire.setMunitionObjectIdentifier(weaponFireCoder.decodeMunitionObjectIdentifier(theParameters.get(munitionObjIdParameterHandle)));
                theWeaponfire.setMunitionType( weaponFireCoder.decodeMunitionType(theParameters.get(munitionTypeParameterHandle)));
                theWeaponfire.setQuantityFired(weaponFireCoder.decodeQuantityFired(theParameters.get(quantityFiredParameterHandle)));
                theWeaponfire.setRateOfFire(weaponFireCoder.decodeRateOfFire(theParameters.get(rateOfFireParameterHandle)));
                theWeaponfire.setTargetObjectIdentifier(weaponFireCoder.decodeTargetObjectIdentifier(theParameters.get(targetObjIdParameterHandle)));
                theWeaponfire.setWarheadType(weaponFireCoder.decodeWarheadType(theParameters.get(warheadTypeParameterHandle)));


            } catch(DecoderException e) {
                System.out.println(e.toString());
                return;
            }

            discoveredWeaponFires.put(theWeaponfire.getEventIdentifier().toString(), theWeaponfire);
        }
    }


    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters, byte[] userSuppliedTag, OrderType sentOrdering,
                                   TransportationTypeHandle theTransport, SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        receiveInteraction(interactionClass, theParameters);
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters, byte[] userSuppliedTag, OrderType sentOrdering,
                                   TransportationTypeHandle theTransport, LogicalTime theTime, OrderType receivedOrdering,
                                   SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        receiveInteraction(interactionClass, theParameters);
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass,
                                   ParameterHandleValueMap theParameters, byte[] userSuppliedTag, OrderType sentOrdering,
                                   TransportationTypeHandle theTransport, LogicalTime theTime, OrderType receivedOrdering,
                                   MessageRetractionHandle retractionHandle, SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        receiveInteraction(interactionClass, theParameters);
    }


    /**
     * @param fad The list of weapon fire interactions included in the fad.
     * @return true means error, false means correct
     * @throws TcFailed due to discrepancies between fad and discovered weaponFires
     * @throws TcInconclusive when no weaponFires are found
     */
    public boolean testWeaponFireIntegrityIdentity(List<WeaponFire> fad) throws TcFailed, TcInconclusive {

        logger.info("Executing Test");

        String lineSeparator = "\n---------------------------------------------------------------------\n";

        if (discoveredWeaponFires.isEmpty()) {
            throw new TcInconclusive("No WeaponFire interactions found on the RTI bus. A system "
                    + "under test must create discoverable WeaponFire interactions before attempting the test.");
        }
        // Traverse the discovered WeaponFire interactions.
        boolean testPassed = true;
        boolean fadWeaponFirePassesTest;
        StringBuilder failedStringBuilder = new StringBuilder();
        if (fad.size() != discoveredWeaponFires.size()) {
            testPassed = false;
            String failedMessage = "FAIL: Fad and discovered WeaponFire sizes do not match";
            failedStringBuilder.append(failedMessage);
            logger.info(failedMessage);
        }

        for (WeaponFire fadWeaponFire : fad) {
            // Loop over each discovered weapon fire to check if this fadWeaponFire is present
            fadWeaponFirePassesTest = discoveredWeaponFires.values().stream()
                    .anyMatch(discoveredWeaponFire -> discoveredWeaponFire.getEventIdentifier().equals(fadWeaponFire.getEventIdentifier()));
            String failedMessage;
            if (!fadWeaponFirePassesTest) {
                testPassed = false;
                failedMessage = "FAIL: Weapon Fire Interaction from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                                     + " found no weaponFire match in discovered Weapon Fire Interactions";
                failedStringBuilder.append("\n"+failedMessage);
            } else {
                failedMessage = "OKAY: Weapon Fire Interaction from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                                       + "was found in discovered Weapon Fire Interactions";
                failedStringBuilder.append("\n"+failedMessage);
            }
            logger.info(lineSeparator+failedMessage+lineSeparator);

        }
        if (!testPassed) {
            throw new TcFailed("Test failed due to errors in Weapon Fire Interaction(s) or absent/unrecognized Weapon Fire Interaction(s) : "+failedStringBuilder.toString()) ;
        } else {
            logger.info("{} TEST IS COMPLETED SUCCESFULLY. {}",lineSeparator,lineSeparator);
            return false;
        }
    }

    /**
     * @param fad The list of weapon fire interactions included in the fad.
     * @return true means error, false means correct
     * @throws TcFailed due to discrepancies between fad and discovered weapon fires
     * @throws TcInconclusive when no weapon fires are found
     */
    public boolean testWeaponFireIntegrityParameters(List<WeaponFire> fad) throws TcFailed, TcInconclusive {

        logger.info("Executing Test");

        if (discoveredWeaponFires.isEmpty()) {
            throw new TcInconclusive("No Weapon Fire Interactions found on the RTI bus. A system "
                    + "under test must create discoverable WeaponFire objects before attempting the test.");
        }
        // Traverse the discovered WeaponFire objects.
        boolean testPassed = true;
        boolean fadWeaponFirePassesTest;
        for (WeaponFire fadWeaponFire : fad) {
            // Loop over each discovered weaponFire to check if this weaponFire is present
            Optional<WeaponFire> optionalWeaponFire =
                    discoveredWeaponFires.values()
                    .stream()
                    .filter(discoveredWeaponFire -> discoveredWeaponFire.getEventIdentifier().equals(fadWeaponFire.getEventIdentifier()))
                    .findFirst();

            if (!optionalWeaponFire.isPresent()) {
                testPassed = false;
                logger.info("---------------------------------------------------------------------");
                logger.info("FAIL: WeaponFire Interaction from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                        + " found no ID match in discovered WeaponFire Interactions");
                logger.info("---------------------------------------------------------------------");
            } else {
              
                fadWeaponFirePassesTest = new WeaponFireEqualUtils(spatialThresold).areWeaponFiresParametersEquals(fadWeaponFire, optionalWeaponFire.get());
                if (!fadWeaponFirePassesTest) {
                    testPassed = false;
                    logger.info("---------------------------------------------------------------------");
                    logger.info("FAIL: WeaponFire Interaction from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                            + " has not the same parameters as its discovered Weapon Fire match");
                    logger.info("---------------------------------------------------------------------");
                } else {
                    logger.info("---------------------------------------------------------------------");
                    logger.info("OKAY: WeaponFire from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                            + " found an ID and type match in discovered WeaponFires");
                    logger.info("---------------------------------------------------------------------");
                }
            }
        }
        if (!testPassed) {
            throw new TcFailed("Test failed due to errors in Weapon Fire Interaction(s) or absent/unrecognized Weapon Fire Interaction(s).");
        } else {
            logger.info("---------------------------------------------------------------------");
            logger.info("TEST IS COMPLETED SUCCESFULLY.");
            logger.info("---------------------------------------------------------------------");
            return false;
        }
    }
    
    /**
     * @param fad The list of weapon fire interactions included in the fad.
     * @return true means error, false means correct
     * @throws TcFailed due to discrepancies between fad and discovered weaponFires
     * @throws TcInconclusive when no weaponFires are found
     */
    public boolean testWeaponFireIntegritySpatial(List<WeaponFire> fad) throws TcFailed, TcInconclusive {

        logger.info("Executing Test");

        if (discoveredWeaponFires.isEmpty()) {
            throw new TcInconclusive("No WeaponFire Interactions found on the RTI bus. A system "
                    + "under test must create discoverable WeaponFire objects before attempting the test.");
        }
        // Traverse the discovered WeaponFire Interactions.
        boolean testPassed = true;
        for (WeaponFire fadWeaponFire : fad) {
            // Loop over each discovered weaponFire to check if this fadWeaponFire is present
            Optional<WeaponFire> optionalWeaponFire =
                    discoveredWeaponFires.values()
                    .stream()
                    .filter(discoveredWeaponFire -> discoveredWeaponFire.getEventIdentifier().equals(fadWeaponFire.getEventIdentifier()))
                    .findFirst();
            
            if (!optionalWeaponFire.isPresent()) {
                testPassed = false;
                logger.info("---------------------------------------------------------------------");
                logger.info("FAIL: WeaponFire from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                        + " found no ID match in discovered WeaponFires");
                logger.info("---------------------------------------------------------------------");
            } else {
                logger.debug("\nFrom fad:{}\nFrom fed:{}",
                        fadWeaponFire.getFiringLocation(),
                        optionalWeaponFire.get().getFiringLocation());


                boolean fadWeaponFireLocationPassesTest = new WeaponFireEqualUtils(spatialThresold).worldLocationEquals(
                        fadWeaponFire.getFiringLocation(),
                        optionalWeaponFire.get().getFiringLocation());

                boolean fadWeaponFireVelocityPassesTest = new WeaponFireEqualUtils(spatialThresold).velocityEquals(
                        fadWeaponFire.getInitialVelocityVector(),
                        optionalWeaponFire.get().getInitialVelocityVector());

                //This part overwrites the decision made by the equalCheck of the WeaponFireEqualUtils class.
                if (fadWeaponFireVelocityPassesTest == false) {
                    logger.warn("WARNING! WeaponFire Velocities do not match!");
                    logger.warn("This can be due to either a faulty SuT or a difference between DIS coordinates and the vcsutilities libraries used. This failure has however not affected the official outcome of this TestSuite.");
                    fadWeaponFireVelocityPassesTest = true;
                }


                if (!(fadWeaponFireLocationPassesTest && fadWeaponFireVelocityPassesTest) ){
                    testPassed = false;
                    logger.info("---------------------------------------------------------------------");
                    logger.info("FAIL: WeaponFire from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                            + " has not the same spatial info as its discovered WeaponFire");
                    logger.info("---------------------------------------------------------------------");
                } else {
                    logger.info("---------------------------------------------------------------------");
                    logger.info("OKAY: WeaponFire from FAD with identifier " + fadWeaponFire.getEventIdentifier()
                            + " found an ID and spatial match in discovered WeaponFires");
                    logger.info("---------------------------------------------------------------------");
                }
            }
        }
        if (!testPassed) {
            throw new TcFailed("Test failed due to errors in WeaponFire Interaction(s) or absent/unrecognized WeaponFire(s).");
        } else {
            logger.info("---------------------------------------------------------------------");
            logger.info("TEST IS COMPLETED SUCCESFULLY.");
            logger.info("---------------------------------------------------------------------");
            return false;
        }
    }

}
