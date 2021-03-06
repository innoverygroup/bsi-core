package eu.inn.biometric.signature.managed.impl;

/*
 * #%L
 * BioSignIn (Biometric Signature Interface) Core [http://www.biosignin.org]
 * IsoSignatureData.java is part of BioSignIn project
 * %%
 * Copyright (C) 2014 Innovery SpA
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */


//import java.awt.Dimension;
//import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Order;
import org.simpleframework.xml.Root;

import eu.inn.biometric.signature.device.CapturingComponent;
import eu.inn.biometric.signature.device.Dimension;
import eu.inn.biometric.signature.device.MetricUnits;
import eu.inn.biometric.signature.device.Point;
import eu.inn.biometric.signature.device.RealSize;
import eu.inn.biometric.signature.device.SignArea;
import eu.inn.biometric.signature.extendeddata.ExtendedData;
import eu.inn.biometric.signature.isoiec19794.y2007.Channel;
import eu.inn.biometric.signature.isoiec19794.y2007.ChannelAttribute;
import eu.inn.biometric.signature.isoiec19794.y2007.ChannelDescription;
import eu.inn.biometric.signature.isoiec19794.y2007.IsoHeader;
import eu.inn.biometric.signature.isoiec19794.y2007.IsoIec19794Signature;
import eu.inn.biometric.signature.isoiec19794.y2007.IsoPoint;
import eu.inn.biometric.signature.managed.IBdi;
import eu.inn.biometric.signature.managed.ManagedIsoPoint;
import eu.inn.biometric.signature.utils.MetricConversion;
import eu.inn.serialization.XmlSerializer;

@Root
public class IsoSignatureData implements IBdi, Cloneable {

	@ElementList()
	private ArrayList<ExtendedData> extendedDatas = new ArrayList<ExtendedData>();

	@Element
	private byte[] isoData;

	private static XmlSerializer<IsoSignatureData> ser = new XmlSerializer<IsoSignatureData>(IsoSignatureData.class);

	public static IsoSignatureData fromXmlDocument(String doc) {
		doc = doc.replace("<extendedDatas><isoData>","<extendedDatas /><isoData>");
		return ser.deserialize(doc);
	}

	public static IsoSignatureData fromIso(byte[] isoData) {
		IsoSignatureData ret = new IsoSignatureData();
		ret.isoData = isoData;
		return ret;
	}

	public IsoIec19794Signature getIsoSignature() {
		if (isoSignature == null)
			try {
				isoSignature = IsoIec19794Signature.FromBytes(isoData);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		return isoSignature;
	}

	List<ManagedIsoPoint> points = null;

	public List<ManagedIsoPoint> getIsoPoints() {
		if (points == null) {
			List<ManagedIsoPoint> ret = new ArrayList<ManagedIsoPoint>();
			for (IsoPoint p : getIsoSignature().getBody().getPoints())
				ret.add(ManagedIsoPoint.fromIsoPoint(p, getIsoSignature().getHeader()));

			points = ret;
		}
		return points;
	}

	public String toOutput() {
		try {
			return ser.serialize(this);
		} catch (Throwable t) {

			t.printStackTrace();

			throw new RuntimeException(t);
		}
	}

	@Override
	public boolean isEncrypted() {
		return false;
	}

	@Override
	public IsoSignatureData clone() throws CloneNotSupportedException {
		return IsoSignatureData.fromXmlDocument(this.toOutput());
	}

	public byte[] getIsoData() {
		return isoData;
	}

	public void setIsoData(byte[] isoData) {
		this.isoData = isoData;
		isoSignature = null;
		device = null;
		points = null;
	}

	public List<ExtendedData> getExtendedDatas() {
		return extendedDatas;
	}

	private IsoIec19794Signature isoSignature = null;

	public void setExtendedDatas(ArrayList<ExtendedData> extendedDatas) {
		this.extendedDatas = extendedDatas;
	}

	CapturingComponent device = null;

	public CapturingComponent getCapturingComponent() throws Exception {
		if (device == null) {
			IsoIec19794Signature signature = getIsoSignature();

			if (signature == null) {
				throw new NullPointerException("signature");
			}
			IsoHeader header = signature.getHeader();
			CapturingComponent toAssign = new CapturingComponent();

			int minX = (int) Math.ceil(header.getChannel(Channel.X)
					.getAttribute(ChannelAttribute.MINIMUN_CHANNEL_VALUE));
			int maxX = (int) Math.ceil(header.getChannel(Channel.X)
					.getAttribute(ChannelAttribute.MAXIMUM_CHANNEL_VALUE));
			int mixY = (int) Math.ceil(header.getChannel(Channel.Y)
					.getAttribute(ChannelAttribute.MINIMUN_CHANNEL_VALUE));
			int maxY = (int) Math.ceil(header.getChannel(Channel.Y)
					.getAttribute(ChannelAttribute.MAXIMUM_CHANNEL_VALUE));
			toAssign.setSignArea(new SignArea(new Point(minX, mixY), new Dimension(maxX - minX, maxY - mixY)));
			toAssign.setRealSize(new RealSize(MetricUnits.Points, toAssign.getSignArea().getDimension()));
			if (header.getChannel(Channel.X).containsAttribute(ChannelAttribute.SCALING_VALUE)
					&& header.getChannel(Channel.Y).containsAttribute(ChannelAttribute.SCALING_VALUE)) {
				double scaledSignatureAreaWidth = (double) toAssign.getRealSize().getDimension().getWidth()
						/ header.getChannel(Channel.X).getAttribute(ChannelAttribute.SCALING_VALUE);
				double scaledSignatureAreaHeight = (double) toAssign.getRealSize().getDimension().getHeight()
						/ header.getChannel(Channel.Y).getAttribute(ChannelAttribute.SCALING_VALUE);
				toAssign.getRealSize().setDimension(
						new Dimension((int) Math.ceil(MetricConversion.ToPdfUnits(scaledSignatureAreaWidth * 1000.0,
								MetricUnits.Millimeters)), (int) Math.ceil(MetricConversion.ToPdfUnits(
								scaledSignatureAreaHeight * 1000.0, MetricUnits.Millimeters))));
			}
			if (header.containsChannel(Channel.Z)) {
				toAssign.getZAxis().setSupported(true);
				toAssign.getZAxis().setMinimum(
						(int) Math.ceil(header.getChannel(Channel.Z).getAttribute(
								ChannelAttribute.MINIMUN_CHANNEL_VALUE)));
				toAssign.getZAxis().setMaximum(
						(int) Math.ceil(header.getChannel(Channel.Z).getAttribute(
								ChannelAttribute.MAXIMUM_CHANNEL_VALUE)));
			}
			if (header.containsChannel(Channel.F)) {
				toAssign.getPressure().setMinimum(
						(short) Math.ceil(header.getChannel(Channel.F).getAttribute(
								ChannelAttribute.MINIMUN_CHANNEL_VALUE)));
				toAssign.getPressure().setMaximum(
						(short) Math.ceil(header.getChannel(Channel.F).getAttribute(
								ChannelAttribute.MAXIMUM_CHANNEL_VALUE)));
				toAssign.getPressure()
						.setPressureSupported(
								(Math.abs((int) (toAssign.getPressure().getMaximum() - toAssign.getPressure()
										.getMinimum())) > 1));

				IsoPoint lastPoint = null;
				boolean airMoveSupported = false;
				boolean timeInAirmove = false;
				for (IsoPoint i : signature.getBody().getPoints()) {

					if (lastPoint == null) {
						lastPoint = i;
						continue;
					}
					if (lastPoint.getProperty(Channel.F) >= 0)
						continue;
					if (i.getProperty(Channel.F) == 0) {
						if ((!lastPoint.getProperty(Channel.X).equals(i.getProperty(Channel.X)) || (!lastPoint
								.getProperty(Channel.Y).equals(i.getProperty(Channel.Y))))) {
							airMoveSupported = true;
							if (header.containsChannel(Channel.DT))
								timeInAirmove = true;
							if (header.containsChannel(Channel.T)
									&& (!lastPoint.getProperty(Channel.T).equals(i.getProperty(Channel.T))))
								timeInAirmove = true;
							break;
						}

					}
					lastPoint = i;
				}
				toAssign.getPressure().setAirmovesSupported(airMoveSupported);
				toAssign.getTimeInfo().setTimeSupportDuringAirMoves(timeInAirmove);
			}
			if (header.containsChannel(Channel.T) || header.containsChannel(Channel.DT)) {
				toAssign.getTimeInfo().setSupported(true);
			}
			if (header.containsChannel(Channel.DT)
					&& header.getChannel(Channel.DT).containsAttribute(ChannelAttribute.CONSTANT)
					&& Math.abs(header.getChannel(Channel.DT).getAttribute(ChannelAttribute.CONSTANT) - 1.0) < 0.01) {
				toAssign.getTimeInfo().setFixedSamplingRate(true);
				toAssign.getTimeInfo().setSamplingRatePointsPerSecond(
						(short) Math.ceil(1000.0 / header.getChannel(Channel.DT).getAttribute(
								ChannelAttribute.SCALING_VALUE)));
			}
			device = toAssign;

		}
		return device;
	}

	public static IsoSignatureData fromData(List<ManagedIsoPoint> clearedPenPoints, CapturingComponent deviceInformation)
			throws Exception {
		double scalingRatioX = 1.0;
		double scalingRatioY = 1.0;
		IsoIec19794Signature signature = new IsoIec19794Signature();
		double width = Math.ceil((double) (((double) (deviceInformation.getSignArea().getRight() - deviceInformation
				.getSignArea().getLeft())))
				/ (((((double) deviceInformation.getRealSize().getDimension().getWidth() / 72.0) * 2.54) / 100.0)));
		double height = Math
				.ceil((double) (((double) (deviceInformation.getSignArea().getTop() - deviceInformation.getSignArea()
						.getBottom())) / (((((double) deviceInformation.getRealSize().getDimension().getHeight()) / 72.0) * 2.54) / 100.0)));
		if (width >  Channel.X.getMaxValue()) {
			scalingRatioX = Channel.X.getMaxValue() / width;
			width =  Channel.X.getMaxValue();
		}
		if (height >  Channel.Y.getMaxValue()) {
			scalingRatioY = Channel.Y.getMaxValue() / height;
			height = Channel.Y.getMaxValue();
		}
		ChannelDescription descX = new ChannelDescription(Channel.X);
		signature.getHeader().putChannel(Channel.X, descX);
		descX.putAttribute(ChannelAttribute.SCALING_VALUE, width);
		descX.putAttribute(ChannelAttribute.MINIMUN_CHANNEL_VALUE, deviceInformation.getSignArea().getLeft()
				* scalingRatioX);
		descX.putAttribute(ChannelAttribute.MAXIMUM_CHANNEL_VALUE, deviceInformation.getSignArea().getRight()
				* scalingRatioX);
		ChannelDescription descY = new ChannelDescription(Channel.Y);

		signature.getHeader().putChannel(Channel.Y, descY);
		descY.putAttribute(ChannelAttribute.SCALING_VALUE, height);
		descY.putAttribute(ChannelAttribute.MINIMUN_CHANNEL_VALUE, deviceInformation.getSignArea().getBottom()
				* scalingRatioY);
		descY.putAttribute(ChannelAttribute.MAXIMUM_CHANNEL_VALUE, deviceInformation.getSignArea().getTop()
				* scalingRatioY);
		if (deviceInformation.getZAxis().isSupported()) {
			ChannelDescription descZ = new ChannelDescription(Channel.Z);
			signature.getHeader().putChannel(Channel.Z, descZ);
			descZ.putAttribute(ChannelAttribute.SCALING_VALUE, 1.0);
			descZ.putAttribute(ChannelAttribute.MINIMUN_CHANNEL_VALUE, (double) deviceInformation.getZAxis()
					.getMinimum());
			descZ.putAttribute(ChannelAttribute.MAXIMUM_CHANNEL_VALUE, (double) deviceInformation.getZAxis()
					.getMaximum());
		}

		ChannelDescription descF = new ChannelDescription(Channel.F); // TODO: Check if F is supported
		signature.getHeader().putChannel(Channel.F, descF);
		descF.putAttribute(ChannelAttribute.SCALING_VALUE, 1.0);
		descF.putAttribute(ChannelAttribute.MINIMUN_CHANNEL_VALUE, (double) deviceInformation.getPressure()
				.getMinimum());
		descF.putAttribute(ChannelAttribute.MAXIMUM_CHANNEL_VALUE, (double) deviceInformation.getPressure()
				.getMaximum());
		if (deviceInformation.getTimeInfo().isSupported())
		// TODO: Maybe set DT if T not supported?
		// (coz T or DT is required)
		{
			ChannelDescription descT = new ChannelDescription(Channel.T);
			signature.getHeader().putChannel(Channel.T, descT);
			descT.putAttribute(ChannelAttribute.SCALING_VALUE, 1000.0);
		}

		for (ManagedIsoPoint packet : clearedPenPoints) {
			IsoPoint point2 = new IsoPoint();
			point2.putProp(Channel.X, (int)Math.round(packet.getX()));
			point2.putProp(Channel.Y, (int)Math.round(packet.getY()));
			point2.putProp(Channel.F, (int) packet.getPressure());
			point2.putProp(Channel.T, (int) packet.getTime());
			if (deviceInformation.getZAxis().isSupported()) {
				// TODO: add Z value when implemented
			}
			signature.getBody().getPoints().add(point2);
		}

		IsoSignatureData ret = new IsoSignatureData();
		ret.setIsoData(signature.toBytes());

		return ret;
	}
}
