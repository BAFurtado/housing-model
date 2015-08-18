package housing;

public class HouseholdStats extends CollectorBase {

	public void step() {
		totalAnnualIncome = 0.0;
		nRenting = 0;
    	nHomeless = 0;
    	nHouseholds = Model.households.size();
    	rentalYield = 0.0;
    	for(Household h : Model.households) {
			totalAnnualIncome += h.getMonthlyPreTaxIncome();
    		if(h.isInSocialHousing()) {
    			++nHomeless;
    		} else if(h.isRenting()) {
    			++nRenting;
    			rentalYield += h.housePayments.get(h.home).monthlyPayment*12.0/Model.housingMarket.getAverageSalePrice(h.home.getQuality());
    		}
    	}
    	if(rentalYield > 0.0) rentalYield /= nRenting;
    	nNonOwner = nHomeless + nRenting;
    	nEmpty = Model.construction.housingStock + nHomeless - nHouseholds;
    	totalAnnualIncome *= 12.0; // annualise
	}

	public double [] getNonOwnerAges() {
		double [] result = new double[(int)nNonOwner];
		int i = 0;
		for(Household h : Model.households) {
			if(!h.isHomeowner() && i < nNonOwner) {
				result[i] = h.lifecycle.age;
				++i;
			}
		}
		while(i < nNonOwner) {
			result[i++] = 0.0;
		}
		return(result);
	}
	public String desNonOwnerAges() {
		return("Ages of Renters and households in social housing");
	}
	public String nameNonOwnerAges() {
		return("Renter and Social-housing ages");
	}
	
	public double [] getOwnerOccupierAges() {
		double [] result = new double[(int)nNonOwner];
		int i = 0;
		for(Household h : Model.households) {
			if(!h.isHomeowner() && i < nNonOwner) {
				result[i] = h.lifecycle.age;
				++i;
			}
		}
		while(i < nNonOwner) {
			result[i++] = 0.0;
		}
		return(result);
	}
	public String desOwnerOccupierAges() {
		return("Ages of owner-occupiers");
	}
	public String nameOwnerOccupierAges() {
		return("Ages of owner-occupiers");
	}
	
	public double getBTLProportion() {
		return(((double)(nEmpty+nRenting))/Model.construction.housingStock);
	}
	public String desBTLProportion() {
		return("Proportion of stock of housing owned by buy-to-let investors");
	}
	public String nameBTLProportion() {
		return("Buy-to-let housing stock proportion");
	}
	
	public double [] getRentalYields() {
		double [] result = new double[nRenting];
		int i = 0;
		for(Household h : Model.households) {
			if(h.isRenting() && i<nRenting) {
				result[i++] = h.housePayments.get(h.home).monthlyPayment*12.0/Model.housingMarket.getAverageSalePrice(h.home.getQuality());
			}
		}
		return(result);
	}
	public String desRentalYields() {
		return("Gross annual rental yield on occupied rental properties");
	}
	public String nameRentalYields() {
		return("Rental Yields");
	}
	
	public double [] getIncomes() {
		double [] result = new double[Model.households.size()];
		int i = 0;
		for(Household h : Model.households) {
			result[i++] = h.annualEmploymentIncome();
		}
		return(result);
	}

	public double [] getBankBalances() {
		double [] result = new double[Model.households.size()];
		int i = 0;
		for(Household h : Model.households) {
			result[i++] = h.bankBalance;
		}
		return(result);
	}

    public int getnRenting() {
		return nRenting;
	}

	public int getnHomeless() {
		return nHomeless;
	}

	public int getnNonOwner() {
		return nNonOwner;
	}

	public int getnHouseholds() {
		return nHouseholds;
	}

	public int getnEmpty() {
		return nEmpty;
	}
	
    public int 		  nRenting;
	public int 		  nHomeless;
    public int 		  nNonOwner;
    public int		  nHouseholds;
    public int		  nEmpty;
	public double	  totalAnnualIncome;
	public double	  rentalYield; // gross annual yield on occupied rental properties
}
