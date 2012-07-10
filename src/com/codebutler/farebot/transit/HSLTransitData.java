/*
 * OrcaTransitData.java
 *
 * Copyright (C) 2011 Eric Butler
 *
 * Authors:
 * Eric Butler <eric@codebutler.com>
 *
 * Thanks to:
 * Karl Koscher <supersat@cs.washington.edu>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.codebutler.farebot.transit;

import android.content.Context;
import android.os.Parcel;

import com.codebutler.farebot.FareBotApplication;
import com.codebutler.farebot.R;
import com.codebutler.farebot.Utils;
import com.codebutler.farebot.mifare.Card;
import com.codebutler.farebot.mifare.DesfireCard;
import com.codebutler.farebot.mifare.DesfireFile;
import com.codebutler.farebot.mifare.DesfireFile.RecordDesfireFile;
import com.codebutler.farebot.mifare.DesfireRecord;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class HSLTransitData extends TransitData
{

    private String        mSerialNumber;
    private double     mBalance;
    private HSLTrip[] mTrips;
    private boolean mHasKausi;
	private long mKausiStart;
	private long mKausiEnd;
	private long mKausiPrevStart;
	private long mKausiPrevEnd;
	private long mKausiPurchasePrice;
	private long mKausiLastUse;
	private long mKausiPurchase;
	private long mLastRefillTime;
	private HSLRefill mLastRefill;
	
	private boolean mKausiNoData;
	private long mLastRefillAmount;
	private long mArvoExit;
	private long mArvoPurchase;
	private long mArvoExpire;
	private long mArvoPax;
	private long mArvoPurchasePrice;
	private long mArvoXfer;
	private long mArvoDiscoGroup;
	private long mArvoMystery1;
	private long mArvoMystery2;
	private long mArvoMystery3;

    private static final long EPOCH = 0x32C97ED0;
    
    public static boolean check (Card card)
    {
        return (card instanceof DesfireCard) && (((DesfireCard) card).getApplication(0x1120ef) != null);
    }

    public static TransitIdentity parseTransitIdentity(Card card)
    {
        try {
            byte[] data = ((DesfireCard) card).getApplication(0x1120ef).getFile(0x08).getData();
            return new TransitIdentity("HSL", Utils.getHexString(data).substring(2, 20));
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL serial", ex);
        }
    }

    public HSLTransitData (Parcel parcel) {
        mSerialNumber = parcel.readString();
        mBalance      = parcel.readDouble();
        
        mArvoMystery1 = parcel.readLong();
        mArvoMystery2 = parcel.readLong();
        mArvoMystery3 = parcel.readLong();
        mArvoExit = parcel.readLong();
        mArvoPurchasePrice = parcel.readLong();
        mArvoDiscoGroup = parcel.readLong();
        mArvoPurchase = parcel.readLong();
        mArvoExpire = parcel.readLong();
        mArvoPax = parcel.readLong();
        mArvoXfer = parcel.readLong();
        
        
        mTrips = new HSLTrip[parcel.readInt()];
        parcel.readTypedArray(mTrips, HSLTrip.CREATOR);
    }
    public static long bitsToLong(int start, int len, byte[] data){
    	long ret=0;
    	for(int i=start;i<start+len;++i){
    		long bit=((data[i/8] >> (7 - i%8)) & 1);
    		ret = ret | (bit << ((start+len-1)-i));
    	}
    	return ret;
    }
    public static long bitsToLong(int start, int len, long[] data){
    	long ret=0;
    	for(int i=start;i<start+len;++i){
    		long bit=((data[i/8] >> (7 - i%8)) & 1);
    		ret = ret | (bit << ((start+len-1)-i));
    	}
    	return ret;
    }
    public HSLTransitData (Card card)
    {
        DesfireCard desfireCard = (DesfireCard) card;

        byte[] data;

        try {
            data = desfireCard.getApplication(0x1120ef).getFile(0x08).getData();
            mSerialNumber = Utils.getHexString(data).substring(2, 20);  //Utils.byteArrayToInt(data, 1, 9);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL serial", ex);
        }

        try {
            data = desfireCard.getApplication(0x1120ef).getFile(0x02).getData();
            mBalance = bitsToLong(0,20,data);
            mLastRefill = new HSLRefill(data);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL refills", ex);
        }
        try {
            data = desfireCard.getApplication(0x1120ef).getFile(0x03).getData();
            mArvoMystery1 = bitsToLong(0,14,data);
            mArvoMystery2 = bitsToLong(14,11,data);
            mArvoMystery3 = bitsToLong(25, 7, data);
            
            mArvoExit = CardDateToTimestamp(bitsToLong(32,14,data), bitsToLong(46,11,data)); 
            mArvoPurchasePrice = bitsToLong(68,14,data);
            mArvoDiscoGroup = bitsToLong(82, 6,data);
            mArvoPurchase = CardDateToTimestamp(bitsToLong(88,14,data), bitsToLong(102,11,data)); //68 price, 82 zone?
            mArvoExpire = CardDateToTimestamp(bitsToLong(113,14,data), bitsToLong(127,11,data)); //68 price, 82 zone?
            mArvoPax = bitsToLong(138,6,data);
           
            mArvoXfer = CardDateToTimestamp(bitsToLong(144,14,data), bitsToLong(158,11,data)); //68 price, 82 zone?
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL value data", ex);
        }
        try {
            mTrips = parseTrips(desfireCard);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL trips", ex);
        }
        try {
        	data = desfireCard.getApplication(0x1120ef).getFile(0x01).getData();
            
            if(bitsToLong(19,14,data)==0 && bitsToLong(67,14,data)==0)
            	mKausiNoData = true;
            mKausiStart = CardDateToTimestamp(bitsToLong(19,14,data),0);
            mKausiEnd = CardDateToTimestamp(bitsToLong(33,14,data),0);
            mKausiPrevStart = CardDateToTimestamp(bitsToLong(67,14,data),0);
            mKausiPrevEnd = CardDateToTimestamp(bitsToLong(81,14,data),0);
            if(mKausiPrevStart > mKausiStart){
            	long temp = mKausiStart;
            	long temp2 = mKausiEnd;
            	mKausiStart = mKausiPrevStart;
            	mKausiEnd = mKausiPrevEnd;
            	mKausiPrevStart = temp;
            	mKausiPrevEnd = temp2;
            }
            mHasKausi = mKausiEnd > (System.currentTimeMillis()/1000.0);
            mKausiPurchase = CardDateToTimestamp(bitsToLong(110,14,data),bitsToLong(124,11,data));
            mKausiPurchasePrice = bitsToLong(149,15,data);
            mKausiLastUse = CardDateToTimestamp(bitsToLong(192,14,data),bitsToLong(206,11,data));
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL kausi data", ex);
        }
    }

    public static long CardDateToTimestamp(long day, long minute) {
    	return (EPOCH) + day * (60*60*24) + minute * 60;
	}

	@Override
    public String getCardName () {
        return "HSL";
    }

    @Override
    public String getBalanceString () {
    	String ret =  NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mBalance / 100);
    	if(mHasKausi)
    		ret +="\n" + GR(R.string.hsl_pass_is_valid);
    	if(mArvoExpire*1000.0 > System.currentTimeMillis())
    		ret += "\n" + GR(R.string.hsl_value_ticket_is_valid) + "!";
        return ret; 
    }
    private static String GR(int r){
    	 return FareBotApplication.getInstance().getResources().getText(r).toString();
    }
    @Override
    public String getCustomString () {
    	StringBuilder ret = new StringBuilder();
    	if(!mKausiNoData){
    		ret.append(GR(R.string.hsl_season_ticket_starts)).append(": ").append(SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(mKausiStart*1000.0));
	    	ret.append("\n");
	    	ret.append(GR(R.string.hsl_season_ticket_ends)).append(": ").append(SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(mKausiEnd*1000.0)); 
	    	ret.append("\n\n");
	    	ret.append(GR(R.string.hsl_season_ticket_bought_on)).append(": ").append(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(mKausiPurchase*1000.0));
	    	ret.append("\n");
	    	ret.append(GR(R.string.hsl_season_ticket_price_was)).append(": ").append(NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mKausiPurchasePrice / 100.0));
	    	ret.append("\n");
	    	ret.append(GR(R.string.hsl_you_last_used_this_ticket)).append(": ").append(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(mKausiLastUse*1000.0));
	    	ret.append("\n");
	    	ret.append(GR(R.string.hsl_previous_season_ticket)).append(": ").append(SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(mKausiPrevStart*1000.0));
	    	ret.append(" - ").append(SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(mKausiPrevEnd*1000.0));
	    	ret.append("\n\n");
    	}

    	ret.append(GR(R.string.hsl_value_ticket)).append(":\n");
    	ret.append(GR(R.string.hsl_value_ticket_bought_on)).append(": ").append(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(mArvoPurchase*1000.0)).append("\n");
    	ret.append(GR(R.string.hsl_value_ticket_expires_on)).append(": ").append(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(mArvoExpire*1000.0)).append("\n");
    	ret.append(GR(R.string.hsl_value_ticket_last_transfer)).append(": ").append(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(mArvoXfer*1000.0)).append("\n");
    	ret.append(GR(R.string.hsl_value_ticket_last_sign)).append(": ").append(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(mArvoExit*1000.0)).append("\n");
    	ret.append(GR(R.string.hsl_value_ticket_price)).append(": ").append(NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mArvoPurchasePrice / 100.0)).append("\n");
    	ret.append(GR(R.string.hsl_value_ticket_disco_group)).append(": ").append(mArvoDiscoGroup).append("\n");
    	ret.append(GR(R.string.hsl_value_ticket_pax)).append(": ").append(mArvoPax).append("\n");
    	ret.append("Mystery1").append(": ").append(mArvoMystery1).append("\n");		
    	ret.append("Mystery2").append(": ").append(mArvoMystery2).append("\n");		
    	ret.append("Mystery3").append(": ").append(mArvoMystery3).append("\n");		
    	
    	if(ret.length()<2)
    		return null;	    	

    	return ret.toString();
    }
    @Override
    public String getSerialNumber () {
        return mSerialNumber;
    }

    @Override
    public Trip[] getTrips () {
        return mTrips;
    }

    @Override
    public Refill[] getRefills () {
    	Refill[] ret ={mLastRefill};
        return ret;
    }

    private HSLTrip[] parseTrips (DesfireCard card)
    {
        DesfireFile file = card.getApplication(0x1120ef).getFile(0x04);

        if (file instanceof RecordDesfireFile) {
            RecordDesfireFile recordFile = (RecordDesfireFile) card.getApplication(0x1120ef).getFile(0x04);

            List<Trip> result = new ArrayList<Trip>();

            HSLTrip[] useLog = new HSLTrip[recordFile.getRecords().length];
            for (int i = 0; i < useLog.length; i++) {
                useLog[i] = new HSLTrip(recordFile.getRecords()[i]);
            }

            Arrays.sort(useLog, new Trip.Comparator());

            return useLog;
        }
        return new HSLTrip[0];
    }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mSerialNumber);
        parcel.writeDouble(mBalance);
        
        parcel.writeLong(mArvoMystery1);
        parcel.writeLong(mArvoMystery2);
        parcel.writeLong(mArvoMystery3);

        parcel.writeLong(mArvoExit);
        parcel.writeLong(mArvoPurchasePrice);
        parcel.writeLong(mArvoDiscoGroup);
        parcel.writeLong(mArvoPurchase);
        parcel.writeLong(mArvoExpire);
        parcel.writeLong(mArvoPax);
        parcel.writeLong(mArvoXfer);
        
        if (mTrips != null) {
            parcel.writeInt(mTrips.length);
            parcel.writeTypedArray(mTrips, flags);
        } else {
            parcel.writeInt(0);
        }
    }
    public static class HSLRefill extends Refill {
    	private final long mRefillTime;
    	private final long mRefillAmount;
    	public HSLRefill(byte[] data) {
            mRefillTime = CardDateToTimestamp(bitsToLong(20,14,data),bitsToLong(34,11,data));
            mRefillAmount = bitsToLong(45,20,data);
    	}
        public HSLRefill (Parcel parcel) {
            mRefillTime = parcel.readLong();
            mRefillAmount = parcel.readLong();
        }
    	
		public void writeToParcel(Parcel dest, int flags) {
	        dest.writeLong(mRefillTime);
	        dest.writeLong(mRefillAmount);
		}

		@Override
		public long getTimestamp() {
			return mRefillTime;
		}

		@Override
		public String getAgencyName() {
			return GR(R.string.hsl_balance_refill);
		}

		@Override
		public String getShortAgencyName() {
			return GR(R.string.hsl_balance_refill);
		}

		@Override
		public long getAmount() {
			return mRefillAmount;
		}

		@Override
		public String getAmountString() {
			return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mRefillAmount / 100.0);
		}
    	
    }
    public static class HSLTrip extends Trip
    {
        private final long mTimestamp;
        private final long mFare;
        private final long mNewBalance;
        private final long mArvo;
		private long mExpireTimestamp;
		private long mPax;

        

        
        public HSLTrip (DesfireRecord record)
        {
            byte[] useData = record.getData();
            long[] usefulData = new long[useData.length];
    
            for (int i = 0; i < useData.length; i++) {
                usefulData[i] = ((long)useData[i]) & 0xFF;
            }
    
            mArvo = bitsToLong(0,1,usefulData);
            
            mTimestamp = CardDateToTimestamp(bitsToLong(1, 14, usefulData), bitsToLong(15,11,usefulData));
            mExpireTimestamp = CardDateToTimestamp(bitsToLong(26,14,usefulData),bitsToLong(40,11,usefulData));
            
            mFare= bitsToLong(51,14,usefulData); 
            
            mPax = bitsToLong(65,5,usefulData);
            		
            mNewBalance=bitsToLong(70,20, usefulData);

        }
        
        public double getExpireTimestamp() {
			return this.mExpireTimestamp;
		}

		public static Creator<HSLTrip> CREATOR = new Creator<HSLTrip>() {
            public HSLTrip createFromParcel(Parcel parcel) {
                return new HSLTrip(parcel);
            }

            public HSLTrip[] newArray(int size) {
                return new HSLTrip[size];
            }
        };

        private HSLTrip (Parcel parcel)
        {   // mArvo, mTimestamp, mExpireTimestamp, mFare, mPax, mNewBalance
        	mArvo  		= parcel.readLong();
        	mTimestamp  = parcel.readLong();
        	mExpireTimestamp  = parcel.readLong();
            mFare       = parcel.readLong();
            mPax 		= parcel.readLong();
            mNewBalance = parcel.readLong();
        }

        @Override
        public long getTimestamp() {
            return mTimestamp;
        }

        @Override
        public String getAgencyName () {
        	if(mArvo!=1)
        		return null;
        	String valid = "\nvalid for " + ((this.mExpireTimestamp - this.mTimestamp) / 60) + "min";
            //if(mAgency==1)
            //    return GR(R.string.hsl_2zone_ticket) + valid;
            return GR(R.string.hsl_1zone_ticket) + valid;
        }

        @Override
        public String getShortAgencyName () {
        	return getAgencyName();
        }

        @Override
        public String getRouteName () {
            if(mArvo==1)
            	return GR(R.string.hsl_balance_ticket) + ", " + mPax + " " + GR(R.string.hsl_person);
            else
           		return GR(R.string.hsl_pass_ticket) + ", " + mPax + " " + GR(R.string.hsl_person);
        }

        @Override
        public String getFareString () {
            return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mFare / 100.0);
        }

        @Override
        public double getFare () {
            return mFare;
        }

        @Override
        public String getBalanceString () {
            return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mNewBalance / 100);
        }



        @Override
        public String getEndStationName () {
            // ORCA tracks destination in a separate record
            return null;
        }

        @Override
        public Station getEndStation () {
            // ORCA tracks destination in a separate record
            return null;
        }

        @Override
        public Mode getMode() {
            return (isLink()) ? Mode.METRO : Mode.BUS;
        }

        public long getCoachNumber() {
            return mPax;
        }

        public void writeToParcel(Parcel parcel, int flags)
        {   // mArvo, mTimestamp, mExpireTimestamp, mFare, mPax, mNewBalance
            parcel.writeLong(mArvo);
            parcel.writeLong(mTimestamp);
            parcel.writeLong(mExpireTimestamp);
            parcel.writeLong(mFare);
            parcel.writeLong(mPax);
            parcel.writeLong(mNewBalance);
        }

        public int describeContents() {
            return 0;
        }

        private boolean isLink () {
            return false;
        }

		@Override
		public String getStartStationName() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Station getStartStation() {
			// TODO Auto-generated method stub
			return null;
		}
    }
}
