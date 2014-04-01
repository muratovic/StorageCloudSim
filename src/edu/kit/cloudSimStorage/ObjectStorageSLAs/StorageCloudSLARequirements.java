/*
 * Title:        StorageCloudSim
 * Description:  StorageCloudSim (Storage as a Service Cloud Simulation), an extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2013, Karlsruhe Institute of Technology, Germany
 * https://github.com/toebbel/StorageCloudSim
 * http://www.tobiassturm.de/projects/storagecloudsim.html
 */
package edu.kit.cloudSimStorage.ObjectStorageSLAs;

import edu.kit.cloudSimStorage.CdmiCloudCharacteristics;
import edu.kit.cloudSimStorage.ObjectStorageSLAs.matchingSLA.*;
import edu.kit.cloudSimStorage.ObjectStorageSLAs.ratingSLA.RakingSum;
import edu.kit.cloudSimStorage.ObjectStorageSLAs.ratingSLA.RateByExportCapabilities;
import edu.kit.cloudSimStorage.ObjectStorageSLAs.ratingSLA.RateByPrice;
import edu.kit.cloudSimStorage.ObjectStorageSLAs.ratingSLA.SLARating;
import edu.kit.cloudSimStorage.monitoring.ILoggable;
import org.simpleframework.xml.*;
import org.simpleframework.xml.core.Persister;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static edu.kit.cloudSimStorage.cdmi.CdmiMetadata.LOCATION;
import static edu.kit.cloudSimStorage.cdmi.CdmiMetadata.MAX_OBJECT_SIZE;

/**
 *
 * @author Tobias Sturm, 6/26/13 5:05 PM */
@Root
public class StorageCloudSLARequirements implements ILoggable {
	@Element(required=false)
	SLARequirement requirements;

	@Element(required=false)
	SLARating ratings;
	Logger logger;

	public StorageCloudSLARequirements() {
		requirements = null;
		ratings = null;
		logger = Logger.getLogger("SLARequest" + java.util.UUID.randomUUID().toString());
	}

	public StorageCloudSLARequirements addRequirement(SLARequirement req) {
		if(requirements == null)
			requirements = req;
		else
			requirements = new SLARequirementAND(requirements, req);

		return this;
	}

	public StorageCloudSLARequirements addRating(SLARating rater) {
		if(ratings == null)
			ratings = rater;
		else
			ratings = new RakingSum(rater, ratings);

		return this;
	}

	public List<CdmiCloudCharacteristics> getMatches(List<CdmiCloudCharacteristics> in) {
		logger.fine("matching characteristics against requirement: " + requirements);
		//create a list <Rating, Chara> for all characteristics, that match all requirements
		List<RatingEntry> ranking = new ArrayList<>();
		for(CdmiCloudCharacteristics c : in) {
			if(requirements == null || requirements.match(c))
				ranking.add(new RatingEntry(0, c));
		}

		logger.fine("raking characteristics against ratings: " + ratings);
		//rank all remaining characteristics
		if(ratings != null) {
			for(RatingEntry rankEntry : ranking) {
				rankEntry.score += ratings.score(rankEntry.characteristics);
			}
		}

		//sort them according to their score
		Collections.sort(ranking);
		Collections.reverse(ranking);

		//unpack characteristics
		int counter = 1;
		List<CdmiCloudCharacteristics> tmp = new ArrayList<>();
		for(RatingEntry entry : ranking) {
			logger.fine(counter + ") " + entry.characteristics.get(CdmiCloudCharacteristics.PROVIDER_NAME));
			tmp.add(entry.characteristics);
		}
		return tmp;
	}


	public StorageCloudSLARequirements minBandiwdth(double minBandwidth) {
		addRequirement(new MinimumCharactersisticValue(CdmiCloudCharacteristics.MIN_BANDWIDTH, minBandwidth));
		return this;
	}

	public StorageCloudSLARequirements maxLatency(double maxLatency) {
		addRequirement(new MaximumCharacteristicsValue(CdmiCloudCharacteristics.MIN_BANDWIDTH, maxLatency, true));
		return this;
	}

	public StorageCloudSLARequirements maxStorageCost(double maxStorageCost) {
		addRequirement(new MaximumCharacteristicsValue(CdmiCloudCharacteristics.MAX_LATENCY, maxStorageCost, true));
		return this;
	}

	public StorageCloudSLARequirements maxUploadCost(double maxStorageCost) {
		addRequirement(new MaximumCharacteristicsValue(CdmiCloudCharacteristics.UPLOAD_COSTS, maxStorageCost, true));
		return this;
	}

	public StorageCloudSLARequirements maxDownloadCost(double maxStorageCost) {
		addRequirement(new MaximumCharacteristicsValue(CdmiCloudCharacteristics.DOWNLOAD_COSTS, maxStorageCost, true));
		return this;
	}

	public StorageCloudSLARequirements minCapacity(long capacity) {
		addRequirement(new MinimumCharactersisticValue(CdmiCloudCharacteristics.AVAILABLE_CAPACITY, capacity));
		return this;
	}

	public StorageCloudSLARequirements maxContainerSizeAtLeast(long capacity) {
		addRequirement(new SLARequirementOR(
				new DoesNotSupportCapability(CdmiCloudCharacteristics.MAX_CONTAINER_SIZE),
				new MinimumCharactersisticValue(CdmiCloudCharacteristics.MAX_CONTAINER_SIZE, capacity)
		));
		return this;
	}

	public StorageCloudSLARequirements maxObjectSizeAtLeast(long capacity) {
		addRequirement(new SLARequirementOR(
				new DoesNotSupportCapability(MAX_OBJECT_SIZE),
				new MinimumCharactersisticValue(MAX_OBJECT_SIZE, capacity)
		));
		return this;
	}

	public StorageCloudSLARequirements locationIs(String location) {
		addRequirement(new CharacteristicMatchesString(LOCATION, location));
		return this;
	}

	public StorageCloudSLARequirements locationIsIn(List<String> locations) {
		if(locations.size() == 1)
			return locationIs(locations.get(0));
		addRequirement(locationIsIn(locations.subList(1, locations.size() - 1), new CharacteristicMatchesString(LOCATION, locations.get(0))));
		return this;
	}

	private SLARequirement locationIsIn(List<String> locations, SLARequirement prev) {
		SLARequirement tmp = new SLARequirementOR(prev, new CharacteristicMatchesString(LOCATION, locations.get(0)));
		locations.remove(0);
		if(locations.isEmpty())
			return tmp;
		return locationIsIn(locations, tmp);
	}

	public StorageCloudSLARequirements hasNoObjectSizeLimit() {
		addRequirement(new SLARequirementOR(
				new DoesNotSupportCapability(MAX_OBJECT_SIZE),
				new MinimumCharactersisticValue(MAX_OBJECT_SIZE, Long.MAX_VALUE)
		));
		return this;
	}

	public StorageCloudSLARequirements hasNoContainerSizeLimit() {
		addRequirement(new SLARequirementOR(
				new DoesNotSupportCapability(CdmiCloudCharacteristics.MAX_CONTAINER_SIZE),
				new MinimumCharactersisticValue(CdmiCloudCharacteristics.MAX_CONTAINER_SIZE, Long.MAX_VALUE)
		));
		return this;
	}

	public StorageCloudSLARequirements canCreateContainers() {
		addRequirement(new SupportsCapability(CdmiCloudCharacteristics.CAPABILITY_CREATE_CONTAINER));
		return this;
	}

	public StorageCloudSLARequirements canDeleteContainers() {
		addRequirement(new SupportsCapability(CdmiCloudCharacteristics.CAPABILITY_DELETE_CONTAINER));
		return this;
	}

	public StorageCloudSLARequirements canModifyMetadata() {
		addRequirement(new SLARequirementAND(
				new SupportsCapability(CdmiCloudCharacteristics.CAPABILITY_READ_METADATA),
				new SupportsCapability(CdmiCloudCharacteristics.CAPABILITY_MOD_METADATA)
		));
		return this;
	}

	public StorageCloudSLARequirements rateByPrice() {
		addRating(new RateByPrice());
		return this;
	}

	public void serialize(OutputStream out) throws Exception {
		Serializer serializer = new Persister();
		serializer.write(this, out);
	}

	public static StorageCloudSLARequirements deserializer(InputStream in) throws Exception {
		Serializer serializer = new Persister();
		return serializer.read(StorageCloudSLARequirements.class, in);
	}

	@Override
	public String toString() {
		return "select (" + requirements + "), order by (" + ratings + ")";
	}

	@Override
	public Logger getLogger() {
		return logger;
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && obj.toString().equals(toString());
		/*return obj.getClass() == this.getClass() &&
				(requirements == null && ((StorageCloudSLARequirements)obj).requirements == null ||
					requirements.equals(((StorageCloudSLARequirements)obj).requirements)) &&
				(ratings == null && ((StorageCloudSLARequirements)obj).ratings == null ||
						ratings.equals(((StorageCloudSLARequirements)obj).ratings));*/
	}

	public StorageCloudSLARequirements rateByCapabilities() {
		addRating(new RateByExportCapabilities());
		return this;
	}

	public StorageCloudSLARequirements clone() throws CloneNotSupportedException {
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			this.serialize(outputStream);
			ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toString().getBytes());
			return StorageCloudSLARequirements.deserializer(inputStream);
		} catch (Exception e) {
			throw new IllegalStateException("cloud not clone SLA request due to IO exception: " + e);
		}
	}
}

class RatingEntry implements Comparable<RatingEntry> {
	public int score;
	public CdmiCloudCharacteristics characteristics;

	RatingEntry(int score, CdmiCloudCharacteristics characteristics) {
		this.score = score;
		this.characteristics = characteristics;
	}

	@Override
	public int compareTo(RatingEntry o) {
		return score - o.score;
	}
}





