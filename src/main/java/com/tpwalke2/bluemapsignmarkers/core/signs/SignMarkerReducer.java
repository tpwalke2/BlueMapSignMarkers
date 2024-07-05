package com.tpwalke2.bluemapsignmarkers.core.signs;

import com.tpwalke2.bluemapsignmarkers.Constants;
import com.tpwalke2.bluemapsignmarkers.core.BlockPosition;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.ActionFactory;
import com.tpwalke2.bluemapsignmarkers.core.bluemap.actions.MarkerAction;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroup;
import com.tpwalke2.bluemapsignmarkers.core.markers.MarkerGroupType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

public class SignMarkerReducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);
    private final ActionFactory actionFactory;
    private final Map<String, MarkerGroup> prefixGroupMap;

    public SignMarkerReducer(ActionFactory actionFactory, Map<String, MarkerGroup> prefixGroupMap) {
        this.actionFactory = actionFactory;
        this.prefixGroupMap = prefixGroupMap;
    }

    public Stream<MarkerAction> reduce(
            SignEntry currentSignEntry,
            SignMarkerOperation signOperation,
            SignState signState) {
        // TODO review all LOGGER.info calls before push
        LOGGER.info("Processing sign: {}", currentSignEntry.key());
        var currentSignDetails = new SignDetails(currentSignEntry);
        var currentMarkerGroupOptional = Optional.ofNullable(currentSignDetails.getPrefix() == null ? null : prefixGroupMap.get(currentSignDetails.getPrefix()));

        var previousSignEntryOptional = signState.getSign(currentSignEntry.key());
        var previousPrefix = previousSignEntryOptional.isEmpty() ? "" : SignEntryHelper.getPrefix(previousSignEntryOptional.get());
        var previousMarkerGroupOptional = Optional.ofNullable(prefixGroupMap.get(previousPrefix));

        // 1. Update the prefix on POI to empty -> remove POI marker
        // 2. Update the prefix on LINE to empty -> remove LINE marker
        // 3. The sign has been removed -> remove POI or LINE marker
        if (previousSignEntryOptional.isPresent()
                && ((currentSignDetails.getPrefix().isEmpty()
                || currentMarkerGroupOptional.isEmpty())
                || signOperation == SignMarkerOperation.REMOVE)) {
            if (previousMarkerGroupOptional.isEmpty()) {
                LOGGER.info("Cannot remove sign, no marker group found for previous prefix: {}", previousPrefix);
                return Stream.empty();
            }

            return removeSign(
                    signState,
                    previousSignEntryOptional.get(),
                    previousMarkerGroupOptional.get());
        }

        if (currentMarkerGroupOptional.isEmpty()) {
            LOGGER.info("Cannot process sign, no marker group found for current prefix: {}", currentSignDetails.getPrefix());
            return Stream.empty();
        }

        if (signOperation == SignMarkerOperation.UPSERT) {
            var currentGroupKey = new SignGroupKey(
                    currentSignDetails.getSignEntry().key().parentMap(),
                    currentMarkerGroupOptional.get(),
                    currentSignDetails.getLabel());

            // 4. Net new POI sign (i.e. previous sign does not exist) -> create new POI marker
            // 5. Net new LINE sign (i.e. previous sign does not exist) -> create new LINE marker
            if (previousSignEntryOptional.isEmpty()) {
                return processNewSign(
                        currentSignDetails,
                        currentMarkerGroupOptional.get(),
                        signState,
                        currentGroupKey);
            }

            var previousSignDetails = new SignDetails(previousSignEntryOptional.get());

            // 6. No change in label, detail, or prefix on POI marker -> no action
            // 7. No change in label or prefix on LINE marker -> no action
            if (currentSignDetails.getLabel().equals(previousSignDetails.getLabel()) && currentSignDetails.getDetail().equals(previousSignDetails.getDetail())) {
                return Stream.empty();
            }

            if (previousMarkerGroupOptional.isEmpty()) {
                LOGGER.info("Cannot update sign, no marker group found for previous prefix: {}", previousSignDetails.getPrefix());
                return Stream.empty();
            }

            return processUpdateSign(
                    currentSignDetails,
                    previousSignDetails,
                    signState,
                    currentMarkerGroupOptional.get(),
                    previousMarkerGroupOptional.get(),
                    currentGroupKey);
        }

        LOGGER.warn("Unknown sign operation: {}", signOperation);
        return Stream.empty();
    }

    private Stream<MarkerAction> processUpdateSign(
            SignDetails currentSignDetails,
            SignDetails previousSignDetails,
            SignState signState,
            MarkerGroup currentMarkerGroup,
            MarkerGroup previousMarkerGroup,
            SignGroupKey currentGroupKey
    ) {
        // 8. Update only the label or detail on POI -> update POI marker
        // 9. Update the prefix on POI to different POI markerGroup -> update POI marker
        if (previousMarkerGroup.type() == MarkerGroupType.POI
                && currentMarkerGroup.type() == MarkerGroupType.POI) {
            return processUpdatePOISign(
                    currentSignDetails,
                    previousSignDetails,
                    signState,
                    currentMarkerGroup);
        }

        // 10. Update the prefix on POI to LINE markerGroup -> remove POI marker and create LINE marker
        if (previousMarkerGroup.type() == MarkerGroupType.POI
                && currentMarkerGroup.type() == MarkerGroupType.LINE) {
            return processUpsertLineSign(
                    currentSignDetails,
                    previousSignDetails,
                    signState,
                    currentMarkerGroup,
                    previousMarkerGroup,
                    currentGroupKey);
        }

        if (currentMarkerGroup.type() == MarkerGroupType.LINE
                && currentMarkerGroup.equals(previousMarkerGroup)) {
            // 11. Update only the label on LINE -> Update LINE marker (if not empty) and create new LINE marker
            if (!currentSignDetails.getLabel().equals(previousSignDetails.getLabel())) {
                return processUpsertLineSign(
                        currentSignDetails,
                        previousSignDetails,
                        signState,
                        currentMarkerGroup,
                        previousMarkerGroup,
                        currentGroupKey);
            }

            // 12. Update the prefix on LINE to different LINE markerGroup -> Update LINE marker (if not empty) and create new LINE marker
            if (!currentSignDetails.getPrefix().equals(previousSignDetails.getPrefix())) {
                return processUpsertLineSign(
                        currentSignDetails,
                        previousSignDetails,
                        signState,
                        currentMarkerGroup,
                        previousMarkerGroup,
                        currentGroupKey);
            }
        }

        // 13. Update the prefix on LINE to POI markerGroup ->  Update LINE marker (if not empty) and create POI marker
        if (previousMarkerGroup.type() == MarkerGroupType.LINE && currentMarkerGroup.type() == MarkerGroupType.POI) {
            signState.removeSign(previousSignDetails.getSignEntry());
            signState.addPoiSign(currentSignDetails.getSignEntry().withNormalizedPlayerId(previousSignDetails.getSignEntry().playerId()));
            return Stream.concat(
                    removeSign(
                            signState,
                            previousSignDetails.getSignEntry(),
                            previousMarkerGroup
                    ),
                    Stream.of(actionFactory.createAddPOIAction(
                            currentSignDetails.getSignEntry().key().x(),
                            currentSignDetails.getSignEntry().key().y(),
                            currentSignDetails.getSignEntry().key().z(),
                            currentSignDetails.getSignEntry().key().parentMap(),
                            currentSignDetails.getLabel(),
                            currentSignDetails.getDetail(),
                            currentMarkerGroup)));
        }


        return Stream.empty();
    }

    private Stream<MarkerAction> processUpsertLineSign(
            SignDetails currentSignDetails,
            SignDetails previousSignDetails,
            SignState signState,
            MarkerGroup currentMarkerGroup,
            MarkerGroup previousMarkerGroup,
            SignGroupKey currentGroupKey) {
        signState.removeSign(previousSignDetails.getSignEntry());
        signState.addLineSign(
                currentGroupKey,
                currentSignDetails
                        .getSignEntry()
                        .withNormalizedPlayerId(previousSignDetails.getSignEntry().playerId()));
        var lineSigns = signState.getLineSigns(currentGroupKey);

        return Stream.concat(
                removeSign(
                        signState,
                        previousSignDetails.getSignEntry(),
                        previousMarkerGroup
                ),
                lineSigns.stream().map(signEntries -> actionFactory.createAddLineAction(
                        currentSignDetails.getSignEntry().key().x(),
                        currentSignDetails.getSignEntry().key().y(),
                        currentSignDetails.getSignEntry().key().z(),
                        currentSignDetails.getSignEntry().key().parentMap(),
                        currentSignDetails.getLabel(),
                        currentMarkerGroup,
                        signEntries
                                .stream()
                                .map(s -> new BlockPosition(s.key().x(), s.key().y(), s.key().z())))));
    }

    private Stream<MarkerAction> processUpdatePOISign(
            SignDetails currentSignDetails,
            SignDetails previousSignDetails,
            SignState signState,
            MarkerGroup currentMarkerGroup) {
        signState.removeSign(previousSignDetails.getSignEntry());
        signState.addPoiSign(currentSignDetails.getSignEntry().withNormalizedPlayerId(previousSignDetails.getSignEntry().playerId()));
        return Stream.of(actionFactory.createUpdatePOIAction(
                currentSignDetails.getSignEntry().key().x(),
                currentSignDetails.getSignEntry().key().y(),
                currentSignDetails.getSignEntry().key().z(),
                currentSignDetails.getSignEntry().key().parentMap(),
                currentSignDetails.getLabel(),
                currentSignDetails.getDetail(),
                currentMarkerGroup));
    }

    private Stream<MarkerAction> removeSign(SignState signState,
                                            SignEntry previousSignEntry,
                                            MarkerGroup previousMarkerGroup) {
        LOGGER.info("Removing sign: {}", previousSignEntry.key());
        signState.removeSign(previousSignEntry);

        return Stream.of(actionFactory.createRemoveMarkerAction(
                previousSignEntry.key().x(),
                previousSignEntry.key().y(),
                previousSignEntry.key().z(),
                previousSignEntry.key().parentMap(),
                previousMarkerGroup));
    }

    private Stream<MarkerAction> processNewSign(
            SignDetails currentSignDetails,
            MarkerGroup currentMarkerGroup,
            SignState signState,
            SignGroupKey currentGroupKey
    ) {
        LOGGER.info("Processing new sign: {}", currentSignDetails.getSignEntry().key());
        if (currentMarkerGroup.type() == MarkerGroupType.POI) {
            signState.addPoiSign(currentSignDetails.getSignEntry());
            return Stream.of(actionFactory.createAddPOIAction(
                    currentSignDetails.getSignEntry().key().x(),
                    currentSignDetails.getSignEntry().key().y(),
                    currentSignDetails.getSignEntry().key().z(),
                    currentSignDetails.getSignEntry().key().parentMap(),
                    currentSignDetails.getLabel(),
                    currentSignDetails.getDetail(),
                    currentMarkerGroup
            ));
        } else if (currentMarkerGroup.type() == MarkerGroupType.LINE) {
            signState.addLineSign(currentGroupKey, currentSignDetails.getSignEntry());
            var lineSigns = signState.getLineSigns(currentGroupKey);
            return lineSigns.stream().map(signEntries -> actionFactory.createAddLineAction(
                    currentSignDetails.getSignEntry().key().x(),
                    currentSignDetails.getSignEntry().key().y(),
                    currentSignDetails.getSignEntry().key().z(),
                    currentSignDetails.getSignEntry().key().parentMap(),
                    currentSignDetails.getLabel(),
                    currentMarkerGroup,
                    signEntries
                            .stream()
                            .map(s -> new BlockPosition(s.key().x(), s.key().y(), s.key().z()))));
        }
        return Stream.empty();
    }
}
