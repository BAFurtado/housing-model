package housing;

import java.util.HashSet;

/**************************************************************************************************
 * Class to represent a mortgage-lender (i.e. a bank or building society), whose only function is
 * to approve/decline mortgage requests, so this is where mortgage-lending policy is encoded
 *
 * @author daniel, davidrpugh, Adrian Carro
 *
 *************************************************************************************************/
public class Bank {

    //------------------//
    //----- Fields -----//
    //------------------//

	// General fields
	private Config	                    config = Model.config; // Passes the Model's configuration parameters object to a private field
    private CentralBank                 centralBank; // Connection to the central bank to ask for policy

    // Bank fields
    public HashSet<MortgageAgreement>	mortgages; // all unpaid mortgage contracts supplied by the bank
    public double		                interestSpread; // current mortgage interest spread above base rate (monthly rate*12)
    private double                      monthlyPaymentFactor; // Monthly payment as a fraction of the principal for non-BTL mortgages
    private double                      monthlyPaymentFactorBTL; // Monthly payment as a fraction of the principal for BTL (interest-only) mortgages

    // Credit supply strategy fields
    private double		                supplyTarget; // target supply of mortgage lending (pounds)
    private double		                supplyVal; // monthly supply of mortgage loans (pounds)
    private int                         nOOMortgagesOverLTI; // Number of mortgages for owner-occupying that go over the LTI cap this time step
    private int                         nOOMortgages; // Total number of mortgages for owner-occupying

    // LTV internal policy thresholds
    private double                      firstTimeBuyerLTVLimit; // Loan-To-Value upper limit for first-time buyer mortgages
    private double                      ownerOccupierLTVLimit; // Loan-To-Value upper limit for owner-occupying mortgages
    private double                      buyToLetLTVLimit; // Loan-To-Value upper limit for buy-to-let mortgages

    // LTI internal policy thresholds
    private double                      firstTimeBuyerLTILimit; // Loan-To-Income internal upper limit for first-time buyer mortgages
    private double                      ownerOccupierLTILimit; // Loan-To-Income internal upper limit for owner-occupying mortgages

    //------------------------//
    //----- Constructors -----//
    //------------------------//

	public Bank(CentralBank centralBank) {
	    this.centralBank = centralBank;
		mortgages = new HashSet<>();
	}

    //-------------------//
    //----- Methods -----//
    //-------------------//

	void init() {
		mortgages.clear();
        setMortgageInterestRate(config.BANK_INITIAL_RATE); // Central Bank must already by initiated at this point!
		resetMonthlyCounters();
        // Setup initial LTV internal policy thresholds
        firstTimeBuyerLTVLimit = config.BANK_MAX_FTB_LTV;
        ownerOccupierLTVLimit= config.BANK_MAX_OO_LTV;
        buyToLetLTVLimit = config.BANK_MAX_BTL_LTV;
        // Setup initial LTI internal policy thresholds
        firstTimeBuyerLTILimit = config.BANK_MAX_FTB_LTI;
        ownerOccupierLTILimit = config.BANK_MAX_OO_LTI;
    }
	
	/**
	 * Redo all necessary monthly calculations and reset counters.
     *
     * @param totalPopulation Current population in the model, needed to scale the target amount of credit
	 */
	public void step(int totalPopulation) {
		supplyTarget = config.BANK_CREDIT_SUPPLY_TARGET*totalPopulation;
		setMortgageInterestRate(recalculateInterestRate());
		resetMonthlyCounters();
	}
	
	/**
	 *  Reset counters for the next month
	 */
	private void resetMonthlyCounters() {
		supplyVal = 0.0;
        nOOMortgagesOverLTI = 0;
        nOOMortgages = 0;
	}
	
	/**
	 * Calculate the mortgage interest rate for next month based on the rate for this month and the resulting demand.
     * This assumes a linear relationship between interest rate and demand, and aims to halve the difference between
     * current demand and the target supply
	 */
	private double recalculateInterestRate() {
	    // TODO: Need to decide whether to keep and calibrate the 1/2 factor or to get rid of it
		double rate = getMortgageInterestRate() + 0.5*(supplyVal - supplyTarget)/config.BANK_D_DEMAND_D_INTEREST;
		if (rate < centralBank.getBaseRate()) rate = centralBank.getBaseRate();
		return rate;
	}
	
	/**
	 * Get the interest rate on mortgages.
	 */
	public double getMortgageInterestRate() { return centralBank.getBaseRate() + interestSpread; }
	

	/**
	 * Set the interest rate on mortgages
	 */
	private void setMortgageInterestRate(double rate) {
		interestSpread = rate - centralBank.getBaseRate();
        recalculateMonthlyPaymentFactor();
	}

    /**
     * Compute the monthly payment factor, i.e., the monthly payment on a mortgage as a fraction of the mortgage
     * principal for both BTL (interest-only) and non-BTL mortgages.
     */
	private void recalculateMonthlyPaymentFactor() {
		double r = getMortgageInterestRate()/config.constants.MONTHS_IN_YEAR;
		monthlyPaymentFactor = r/(1.0 - Math.pow(1.0 + r, -config.derivedParams.N_PAYMENTS));
        monthlyPaymentFactorBTL = r;
	}

	/**
	 * Get the monthly payment factor, i.e., the monthly payment on a mortgage as a fraction of the mortgage principal.
	 */
	private double getMonthlyPaymentFactor(boolean isHome) {
		if (isHome) {
			return monthlyPaymentFactor; // Monthly payment factor to pay off the principal in N_PAYMENTS
		} else {
			return monthlyPaymentFactorBTL; // Monthly payment factor for interest-only mortgages
		}
	}

	/**
	 * Method to arrange a Mortgage and get a MortgageAgreement object.
	 * 
	 * @param h The household requesting the mortgage
	 * @param housePrice The price of the house that household h wants to buy
	 * @param isHome True if household h plans to live in the house (non-BTL mortgage)
	 * @return The MortgageApproval object, or NULL if the mortgage is declined
	 */
	MortgageAgreement requestLoan(Household h, double housePrice, double desiredDownPayment, boolean isHome) {
		MortgageAgreement approval = requestApproval(h, housePrice, desiredDownPayment, isHome);
		if(approval == null) return(null);
		// --- if all's well, go ahead and arrange mortgage
		supplyVal += approval.principal;
		if(approval.principal > 0.0) {
			mortgages.add(approval);
			Model.creditSupply.recordLoan(h, approval);
            if(isHome) {
                ++nOOMortgages;
                if(approval.principal/h.getAnnualGrossEmploymentIncome() >
                        centralBank.getLoanToIncomeLimit(h.isFirstTimeBuyer(), isHome)) {
                    ++nOOMortgagesOverLTI;
				}
			}
		}
		return approval;
	}

	/**
	 * Method to request a mortgage approval but not actually sign a mortgage contract, i.e., the returned
     * MortgageAgreement object is not added to the Bank's list of mortgages nor entered into CreditSupply statistics.
     * This is useful for households to explore the details of the best available mortgage contract before deciding
     * whether to actually go ahead and sign it.
	 *
     * @param h The household requesting the mortgage
     * @param housePrice The price of the house that household h wants to buy
     * @param isHome True if household h plans to live in the house (non-BTL mortgage), False otherwise
     * @return The MortgageApproval object, or NULL if the mortgage is declined
	 */
	MortgageAgreement requestApproval(Household h, double housePrice, double desiredDownPayment, boolean isHome) {
	    // Create a MortgageAgreement object to store and return the new mortgage data
		MortgageAgreement approval = new MortgageAgreement(h, !isHome);

        /*
         * Constraints for all mortgages
         */

		// Loan-To-Value (LTV) constraint: it sets a maximum value for the ratio of the principal divided by the house
        // price
		approval.principal = housePrice * getLoanToValueLimit(h.isFirstTimeBuyer(), isHome);

		/*
		 * Constraints specific to non-BTL mortgages
		 */

		if (isHome) {
			// Affordability constraint: it sets a maximum value for the monthly mortgage payment divided by the
            // household's monthly net employment income
			double affordable_principal = config.CENTRAL_BANK_AFFORDABILITY_COEFF * h.getMonthlyNetEmploymentIncome()
                    / getMonthlyPaymentFactor(true);
			approval.principal = Math.min(approval.principal, affordable_principal);
			// Loan-To-Income (LTI) constraint: it sets a maximum value for the principal divided by the household's
            // annual gross employment income
			double lti_principal = h.getAnnualGrossEmploymentIncome() * getLoanToIncomeLimit(h.isFirstTimeBuyer(),
                    true);
			approval.principal = Math.min(approval.principal, lti_principal);

        /*
         * Constraints specific to BTL mortgages
         */

		} else {
            // Interest Coverage Ratio (ICR) constraint: it sets a minimum value for the expected annual rental income
            // divided by the annual interest expenses
			double icr_principal = Model.rentalMarketStats.getExpAvFlowYield() * housePrice
                    / (centralBank.getInterestCoverRatioLimit(false) * getMortgageInterestRate());
			approval.principal = Math.min(approval.principal, icr_principal);
		}

        /*
         * Compute the down-payment
         */

        // Start by assuming the minimum possible down-payment, i.e., that resulting from the above maximisation of the
        // principal available to the household, given its chosen house price
        approval.downPayment = housePrice - approval.principal;
        // Determine the liquid wealth of the household, with no home equity added, as home-movers always sell their
        // homes before bidding for new ones
        double liquidWealth = h.getBankBalance();
		// Ensure desired down-payment is between zero and the house price, capped also by the household's liquid wealth
		if (desiredDownPayment < 0.0) desiredDownPayment = 0.0;
		if (desiredDownPayment > housePrice) desiredDownPayment = housePrice;
        if (desiredDownPayment > liquidWealth) desiredDownPayment = liquidWealth;
		// If the desired down-payment is larger than the initially assumed minimum possible down-payment, then set the
        // down-payment to the desired value and update the principal accordingly
		if (desiredDownPayment > approval.downPayment) {
			approval.downPayment = desiredDownPayment;
			approval.principal = housePrice - desiredDownPayment;
		}

        /*
         * Set the rest of the variables of the MortgageAgreement object
         */

		approval.monthlyPayment = approval.principal * getMonthlyPaymentFactor(isHome);
		approval.nPayments = config.derivedParams.N_PAYMENTS;
		approval.monthlyInterestRate = getMortgageInterestRate() / config.constants.MONTHS_IN_YEAR;
		approval.purchasePrice = approval.principal + approval.downPayment;
		// Throw error and stop program if requested mortgage has down-payment larger than household's liquid wealth
        if (approval.downPayment > liquidWealth) {
            System.out.println("Error at Bank.requestApproval(), down-payment larger than household's bank balance: "
                    + "downpayment = " + approval.downPayment + ", bank balance = " + liquidWealth);
            System.exit(0);
        }

		return approval;
	}

	/**
	 * Find, for a given household, the maximum house price that this mortgage-lender is willing to approve a mortgage
     * for.
	 * 
	 * @param h The household applying for the mortgage
     * @param isHome True if household h plans to live in the house (non-BTL mortgage), False otherwise
	 * @return A double with the maximum house price that this mortgage-lender is willing to approve a mortgage for
	 */
	double getMaxMortgagePrice(Household h, boolean isHome) {
		double max_price;
		double affordable_max_price;
		double lti_max_price;
		double icr_max_price;
        // Determine the liquid wealth of the household, with no home equity added, as home-movers always sell their
        // homes before bidding for new ones
		double liquidWealth = h.getBankBalance();
        double max_downpayment = liquidWealth - 0.01; // Maximum down-payment the household could make, where 1 cent is subtracted to avoid rounding errors

        // LTV constraint: maximum house price the household could pay with the maximum mortgage the bank could provide
        // to the household given the Loan-To-Value limit and the maximum down-payment the household could make
		max_price = max_downpayment/(1.0 - getLoanToValueLimit(h.isFirstTimeBuyer(), isHome));

		if(isHome) { // No LTI nor affordability constraints for BTL investors
			// Affordability constraint // Affordability (disposable income) constraint for maximum house price
            affordable_max_price = max_downpayment + Math.max(0.0, config.CENTRAL_BANK_AFFORDABILITY_COEFF
                    * h.getMonthlyNetEmploymentIncome())/getMonthlyPaymentFactor(isHome);
			max_price = Math.min(max_price, affordable_max_price);
            // Loan-To-Income constraint // Loan to income constraint for maximum house price
			lti_max_price = h.getAnnualGrossEmploymentIncome()*getLoanToIncomeLimit(h.isFirstTimeBuyer(), isHome)
                    + max_downpayment;
			max_price = Math.min(max_price, lti_max_price);
		} else {
		    // Interest Coverage Ratio constraint, setting a minimum annual rental income divided by annual interest expenses // Interest cover ratio constraint for maximum house price
			icr_max_price = max_downpayment/(1.0 - Model.rentalMarketStats.getExpAvFlowYield()
                    /(centralBank.getInterestCoverRatioLimit(isHome)*getMortgageInterestRate()));
			if (icr_max_price < 0.0) icr_max_price = Double.POSITIVE_INFINITY; // When rental yield is larger than interest rate times ICR, then ICR does never constrain
            max_price = Math.min(max_price,  icr_max_price);
        }

        return max_price;
	}

    /**
     * This method removes a mortgage contract by removing it from the HashSet of mortgages
     *
     * @param mortgage The MortgageAgreement object to be removed
     */
    void endMortgageContract(MortgageAgreement mortgage) { mortgages.remove(mortgage); }

    //----- Mortgage policy methods -----//

    /**
     * Get the Loan-To-Value ratio limit applicable by this private bank to a given household. Note that this limit is
     * self-imposed by the private bank.
     *
     * @param isFirstTimeBuyer True if the household is a first-time buyer
     * @param isHome True if the mortgage is to buy a home for the household (non-BTL mortgage)
     * @return The Loan-To-Value ratio limit applicable to the given household
     */
    private double getLoanToValueLimit(boolean isFirstTimeBuyer, boolean isHome) {
        if(isHome) {
            if(isFirstTimeBuyer) {
                return firstTimeBuyerLTVLimit;
            } else {
                return ownerOccupierLTVLimit;
            }
        }
        return buyToLetLTVLimit;
    }

	/**
	 * Get the Loan-To-Income ratio limit applicable by this private bank to a given household. Note that Loan-To-Income
     * constraints apply only to non-BTL applicants. The private bank always imposes its own (hard) limit. Apart from
     * this, it also imposes the Central Bank regulated limit, which allows for a certain fraction of residential loans
     * (mortgages for owner-occupying) to go over it (and thus it is considered here a soft limit).
	 *
	 * @param isFirstTimeBuyer true if the household is a first-time buyer
     * @param isHome True if the mortgage is to buy a home for the household (non-BTL mortgage)
	 * @return The Loan-To-Income ratio limit applicable to the given household
	 */
	private double getLoanToIncomeLimit(boolean isFirstTimeBuyer, boolean isHome) {
	    double limit;
	    // First compute the private bank self-imposed (hard) limit, which applies always
        if (isHome) {
            if (isFirstTimeBuyer) {
                limit = firstTimeBuyerLTILimit;
            } else {
                limit = ownerOccupierLTILimit;
            }
        } else {
            System.out.println("Strange: The bank is trying to impose a Loan-To-Income limit on a Buy-To-Let" +
                    "investor!");
            limit = 0.0; // Dummy limit value
        }
        // If the fraction of non-BTL mortgages already underwritten over the Central Bank LTI limit exceeds a certain
        // maximum (regulated also by the Central Bank)...
        if ((nOOMortgagesOverLTI + 1.0)/(nOOMortgages + 1.0) >
                centralBank.getMaxFractionOOMortgagesOverLTILimit()) {
            // ... then compare the Central Bank LTI (soft) limit and that of the private bank (hard) and choose the smallest
            limit = Math.min(limit, centralBank.getLoanToIncomeLimit(isFirstTimeBuyer, isHome));
        }
		return limit;
    }
}
