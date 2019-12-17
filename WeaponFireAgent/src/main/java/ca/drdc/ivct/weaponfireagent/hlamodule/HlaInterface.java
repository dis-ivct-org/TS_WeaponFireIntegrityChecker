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

package ca.drdc.ivct.weaponfireagent.hlamodule;

import ca.drdc.ivct.coders.warfare.WeaponFireCoder;
import ca.drdc.ivct.fom.warfare.WeaponFire;
import hla.rti1516e.*;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/*
 * This class implements the HlaInterface.
 */
public class HlaInterface extends NullFederateAmbassador {
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

    private static Logger logger = LoggerFactory.getLogger(HlaInterface.class);

    private static final String DEFAULT_FAILURE_MSG = "HlaInterfaceFailure";

    private RTIambassador ambassador;
    private EncoderFactory encoderFactory;

    private InteractionClassHandle _WeaponFireInteractionClassHandle;
    private ParameterHandle _eventIdParameterHandle;
    private ParameterHandle _fireControlSolRangeParameterHandle;
    private ParameterHandle _fireMissionIndexParameterHandle;
    private ParameterHandle _firingLocParameterHandle;
    private ParameterHandle _firingObjIdParameterHandle;
    private ParameterHandle _fuseTypeParameterHandle;
    private ParameterHandle _initVelVectorParameterHandle;
    private ParameterHandle _munitionObjIdParameterHandle;
    private ParameterHandle _munitionTypeParameterHandle;
    private ParameterHandle _quantityFiredParameterHandle;
    private ParameterHandle _rateOfFireParameterHandle;
    private ParameterHandle _targetObjIdParameterHandle;
    private ParameterHandle _warheadTypeParameterHandle;

    /**
     * Connect to a CRC and join federation
     *
     * @param localSettingsDesignator The name to load settings for or "" to load default settings
     * @param fomPath                 path to FOM file
     * @param federationName          Name of the federation to join
     * @param federateName            The name you want for your federate
     * @throws RestoreInProgress                                the action cannot be done because the system is restoring state
     * @throws SaveInProgress                                   the action cannot be done because the system is saving state
     * @throws NotConnected                                     if the federate is not connected to a CRC
     * @throws RTIinternalError                                 if the RTI fail unexpectedly
     * @throws ConnectionFailed                                 if the RTI fail
     * @throws InvalidLocalSettingsDesignator                   InvalidLocalSettingsDesignator
     * @throws ErrorReadingFDD                                  if error with the FDD
     * @throws CouldNotOpenFDD                                  if error with the FDD
     * @throws InconsistentFDD                                  if error with the FDD
     */
    public void start(String localSettingsDesignator, String fomPath, String federationName, String federateName)
            throws RestoreInProgress, SaveInProgress, NotConnected,
        RTIinternalError, ConnectionFailed, InvalidLocalSettingsDesignator, ErrorReadingFDD, CouldNotOpenFDD,
            InconsistentFDD {

        RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
        ambassador = rtiFactory.getRtiAmbassador();
        this.encoderFactory = rtiFactory.getEncoderFactory();

        try {
            ambassador.connect(this, CallbackModel.HLA_IMMEDIATE, localSettingsDesignator);
        } catch (AlreadyConnected ignored) {
        } catch (UnsupportedCallbackModel | CallNotAllowedFromWithinCallback e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }

        try {
            ambassador.destroyFederationExecution(federationName);
        } catch (FederatesCurrentlyJoined e) {
            System.out.println(this.toString() + ": Tried to destroy federation " + federationName
                + " but federation still has active federates.");
        } catch (FederationExecutionDoesNotExist ignored) {
        }

        URL[] url = loadFomModules(fomPath);
        try {
            ambassador.createFederationExecution(federationName, url);
        } catch (FederationExecutionAlreadyExists e) {
            System.out.println(this.toString() + ": Tried to create federation " + federationName
                + " but the federation already exists.");
        }

        try {
            boolean joined = false;
            String federateNameSuffix = "";
            int federateNameIndex = 1;
            while (!joined) {
                try {
                    ambassador.joinFederationExecution(federateName + federateNameSuffix,
                        "WFSimJ", federationName, url);
                    joined = true;
                } catch (FederateNameAlreadyInUse e) {
                    federateNameSuffix = "-" + federateNameIndex++;
                }
            }
        } catch (CouldNotCreateLogicalTimeFactory | FederationExecutionDoesNotExist | CallNotAllowedFromWithinCallback e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } catch (FederateAlreadyExecutionMember ignored) { }

        try {
            getHandles();
            publishInteractions();
        } catch (FederateNotExecutionMember e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    }

    /**
     * Resign and disconnect from CRC
     *
     * @throws RTIinternalError if an internal error happen when stopping.
     */
    public void stop() throws RTIinternalError {
        if (ambassador == null) {
            logger.warn("HlaInterfaceImpl.stop: abassador doesn't exist!");
            return;
        }
        try {
            try {
                ambassador.resignFederationExecution(ResignAction.CANCEL_THEN_DELETE_THEN_DIVEST);
            } catch (FederateNotExecutionMember ignored) {
                logger.error("HlaInterface.stop: FederateNotExecutionMember exception: ", ignored);
            } catch (FederateOwnsAttributes e) {
                logger.error("HlaInterface.stop: FederateOwnsAttributes exception: ", e);
                throw new RTIinternalError(DEFAULT_FAILURE_MSG, e);
            } catch (OwnershipAcquisitionPending e) {
                logger.error("HlaInterface.stop: OwnershipAcquisitionPending exception: ", e);
                throw new RTIinternalError(DEFAULT_FAILURE_MSG, e);
            } catch (CallNotAllowedFromWithinCallback e) {
                logger.error("HlaInterface.stop: CallNotAllowedFromWithinCallback1 exception: ", e);
                throw new RTIinternalError(DEFAULT_FAILURE_MSG, e);
            } catch (InvalidResignAction e) {
                logger.error("HlaInterface.stop: InvalidResignAction exception: ", e);
                throw new RTIinternalError(DEFAULT_FAILURE_MSG, e);
            }

            try {
                ambassador.disconnect();
            } catch (FederateIsExecutionMember e) {
                logger.error("HlaInterface.stop: FederateIsExecutionMember exception: ", e);
                throw new RTIinternalError(DEFAULT_FAILURE_MSG, e);
            } catch (CallNotAllowedFromWithinCallback e) {
                logger.error("HlaInterface.stop: CallNotAllowedFromWithinCallback exception: ", e);
                throw new RTIinternalError(DEFAULT_FAILURE_MSG, e);
            }
        } catch (NotConnected ignored) {
            logger.error("HlaInterface.stop: NotConnected exception: ", ignored);
        }
    }

    private void getHandles() throws RTIinternalError, FederateNotExecutionMember, NotConnected {
        try {
            _WeaponFireInteractionClassHandle = ambassador.getInteractionClassHandle(WEAPONFIRE);
            _eventIdParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, EVENT_ID);
            _fireControlSolRangeParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, FIRE_CONTROL_SOL_RANGE);
            _fireMissionIndexParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, FIRE_MISSION_INDEX);
            _firingLocParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, FIRING_LOC);
            _firingObjIdParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, FIRING_OBJ_ID);
            _fuseTypeParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, FUSE_TYPE);
            _initVelVectorParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, INIT_VEL_VECT);
            _munitionObjIdParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, MUNIT_OBJ_ID);
            _munitionTypeParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, MUNIT_TYPE);
            _quantityFiredParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, QUANT_FIRED);
            _rateOfFireParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, RATE_OF_FIRE);
            _targetObjIdParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, TARGET_OBJ_ID);
            _warheadTypeParameterHandle = ambassador.getParameterHandle(_WeaponFireInteractionClassHandle, WARHEAD_TYPE);
        } catch (NameNotFound | InvalidInteractionClassHandle e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    }

    private void publishInteractions() throws FederateNotExecutionMember, RestoreInProgress,
        SaveInProgress, NotConnected, RTIinternalError {
        try {
            ambassador.publishInteractionClass(_WeaponFireInteractionClassHandle);
        } catch (InteractionClassNotDefined e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    }

    @Override
    public void connectionLost(String faultDescription) throws FederateInternalError {
        logger.error("HlaInterfaceImpl.connectionLost: Lost Connection because: {}", faultDescription);
    }

    /**
     * Create a base entity in the federate and publish it to the federation.
     *
     * @param weaponFire the base entity to inject in the federation
     * @throws FederateNotExecutionMember the federate is not a member
     * @throws RestoreInProgress          the action cannot be done because the system is restoring state
     * @throws SaveInProgress             the action cannot be done because the system is saving state
     * @throws NotConnected               if the federate is not connected to a CRC
     * @throws RTIinternalError           if the RTI fail unexpectedly
     */
    public void createWeaponFire(WeaponFire weaponFire)
            throws FederateNotExecutionMember, RestoreInProgress, SaveInProgress, NotConnected, RTIinternalError {

        try {
            ParameterHandleValueMap theParameters = ambassador.getParameterHandleValueMapFactory().create(13);

            WeaponFireCoder weaponFireCoder = new WeaponFireCoder(encoderFactory);
            weaponFireCoder.setValues(weaponFire);

            theParameters.put(_eventIdParameterHandle, weaponFireCoder.getEventIdCoder().toByteArray());
            theParameters.put(_fireControlSolRangeParameterHandle, weaponFireCoder.getFireControlSolutionRange().toByteArray());
            theParameters.put(_fireMissionIndexParameterHandle, weaponFireCoder.getFireMissionIndex().toByteArray());
            theParameters.put(_firingLocParameterHandle, weaponFireCoder.getFiringLocStructCoder().toByteArray());
            theParameters.put(_firingObjIdParameterHandle, weaponFireCoder.getFiringObjIdCoder().toByteArray());
            theParameters.put(_fuseTypeParameterHandle, weaponFireCoder.getFuseTypeEnum16().toByteArray());
            theParameters.put(_initVelVectorParameterHandle, weaponFireCoder.getVelVectorStructCoder().toByteArray());
            theParameters.put(_munitionObjIdParameterHandle, weaponFireCoder.getMunitionObjIdCoder().toByteArray());
            theParameters.put(_munitionTypeParameterHandle, weaponFireCoder.getMunitionTypeStructCoder().toByteArray());
            theParameters.put(_quantityFiredParameterHandle, weaponFireCoder.getQuantityFired().toByteArray());
            theParameters.put(_rateOfFireParameterHandle, weaponFireCoder.getRateOfFire().toByteArray());
            theParameters.put(_targetObjIdParameterHandle, weaponFireCoder.getTargetObjIdCoder().toByteArray());
            theParameters.put(_warheadTypeParameterHandle, weaponFireCoder.getWarheadTypeEnum16().toByteArray());

            ambassador.sendInteraction(_WeaponFireInteractionClassHandle, theParameters, null);
        } catch (InteractionClassNotPublished | InteractionParameterNotDefined | InteractionClassNotDefined e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    }

    private URL[] loadFomModules(String pathToFomDirectory) {
        List<URL> urls = new ArrayList<>();
        File dir = null;
        try {
            dir = new File(pathToFomDirectory);
        } catch (NullPointerException e) {
            logger.error("No path to FOM directory provided. Check \"fom\" path in config file.", e);
            System.exit(0);
        }

        // Fill a list of URLs.
        File[] dirListing = dir.listFiles();
        if (dirListing != null) {
            for (File child : dirListing) {
                try {
                    urls.add(child.toURI().toURL());
                } catch (MalformedURLException e) {
                    logger.error("File not found at url : {}", child.toURI(), e);
                }
            }
        }

        // Convert the List<URL> to URL[]
        return urls.toArray(new URL[urls.size()]);
    }
}
