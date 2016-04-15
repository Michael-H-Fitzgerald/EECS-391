package edu.cwru.sepia.agent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionFeedback;
import edu.cwru.sepia.action.ActionResult;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.environment.model.history.DamageLog;
import edu.cwru.sepia.environment.model.history.DeathLog;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

public class RLAgent extends Agent {
	private static final long serialVersionUID = 1L;
	private static final String FOOTMAN_UNIT_NAME = "footman";
	private static final int NUM_LEARNING_EPISODES = 10;
	private static final int NUM_EVALUATING_EPISODES = 5;
	public static final double GAMMA = 0.9;
	public static final double LEARNING_RATE = .0001;
	public static final double EPSILON = .02;

	/**
	 * Convenience variable specifying enemy agent number. Use this whenever referring
	 * to the enemy agent. We will make sure it is set to the proper number when testing your code.
	 */
	public static final int ENEMY_PLAYERNUM = 1;

	/** 
	 * Use this random number generator for your epsilon exploration. When you submit we will
	 * change this seed so make sure that your agent works for more than the default seed.
	 */
	public final Random random = new Random(12345);

	public static final int NUM_FEATURES = 2;

	public final int numEpisodes;
	public int currentEpisode = 0;
	public int currentPhaseEpisodeCount = 0;
	public boolean inEvaluationEpisode = true;
	public List<Double> averageRewards = new ArrayList<Double>(10);
	public double cumulativeReward = 0.0;

	private List<Integer> myFootmen;
	private List<Integer> enemyFootmen;
	public Double[] weights;

	public RLAgent(int playernum, String[] args) {
		super(playernum);

		if (args.length >= 1) {
			numEpisodes = Integer.parseInt(args[0]);
			System.out.println("Running " + numEpisodes + " episodes.");
		} else {
			numEpisodes = 10;
			System.out.println("Warning! Number of episodes not specified. Defaulting to 10 episodes.");
		}

		boolean loadWeights = false;
		if (args.length >= 2) {
			loadWeights = Boolean.parseBoolean(args[1]);
		} else {
			System.out.println("Warning! Load weights argument not specified. Defaulting to not loading.");
		}

		if (loadWeights) {
			weights = loadWeights();
		} else {
			// initialize weights to random values between -1 and 1
			weights = new Double[NUM_FEATURES];
			for (int i = 0; i < weights.length; i++) {
				weights[i] = random.nextDouble() * 2 - 1;
			}
		}
	}

	@Override
	public Map<Integer, Action> initialStep(State.StateView stateView, History.HistoryView historyView) {
		System.out.println("In Evaluation: " + inEvaluationEpisode);
		myFootmen = findFootmen(stateView, playernum);
		enemyFootmen = findFootmen(stateView, ENEMY_PLAYERNUM);
		return middleStep(stateView, historyView);
	}

	private List<Integer> findFootmen(State.StateView stateView, int controllerId){
		List<Integer> footmen = new LinkedList<>();
		for (Integer unitId : stateView.getUnitIds(controllerId)) {
			Unit.UnitView unit = stateView.getUnit(unitId);
			String unitName = unit.getTemplateView().getName().toLowerCase();
			if (unitName.equals(FOOTMAN_UNIT_NAME)) {
				footmen.add(unitId);
			} else {
				System.err.println("Unknown unit type: " + unitName);
			}
		}
		return footmen;
	}

	/**
	 *
	 * @return New actions to execute or nothing if an event has not occurred.
	 */
	@Override
	public Map<Integer, Action> middleStep(State.StateView stateView, History.HistoryView historyView) {
		int previousTurnNumber = stateView.getTurnNumber() - 1;
		Map<Integer, Action> marchingOrders = new HashMap<>();
		if(previousTurnNumber < 0){
			return marchingOrders;
		}
		
		for(DeathLog deathLog : historyView.getDeathLogs(previousTurnNumber)){
			if(deathLog.getController() == ENEMY_PLAYERNUM){
				enemyFootmen.remove(((Integer) deathLog.getDeadUnitID()));
			} else {
				myFootmen.remove(((Integer) deathLog.getDeadUnitID()));
				double reward = calculateReward(stateView, historyView, deathLog.getDeadUnitID());
				cumulativeReward = cumulativeReward + reward;
			}
//			System.out.println("Player: " + deathLog.getController() + " unit: " + deathLog.getDeadUnitID());    			
		}

		Map<Integer, ActionResult> actionResults = historyView.getCommandFeedback(playernum, previousTurnNumber);
		for(ActionResult result : actionResults.values()) {
//			System.out.println(result.toString());
			if(result.getFeedback().equals(ActionFeedback.FAILED)){
				marchingOrders.put(result.getAction().getUnitId(), result.getAction());
			}
		}

		for(Integer attackerId : myFootmen){
			double reward = calculateReward(stateView, historyView, attackerId);
			cumulativeReward = cumulativeReward + reward;
			if(!marchingOrders.containsKey(attackerId)){
				if(!actionResults.containsKey(attackerId) || actionResults.get(attackerId).getFeedback().equals(ActionFeedback.COMPLETED)){
					int defenderId = 1;
					if(!inEvaluationEpisode){
						double[] oldWeights = new double[NUM_FEATURES];
						for(int i = 0; i < NUM_FEATURES; i++){
							oldWeights[i] = weights[i];
						}
						double[] newWeights = 
								updateWeights(	oldWeights, 
										calculateFeatureVector(stateView, historyView, attackerId, defenderId),
										reward,
										stateView,
										historyView,
										attackerId);
						for(int i = 0; i < NUM_FEATURES; i++){
							weights[i] = newWeights[i];
						}
					}
					int enemyId = selectAction(stateView, historyView, attackerId);
					Action action = Action.createCompoundAttack(attackerId, enemyId);
					marchingOrders.put(attackerId, action);
				}
			}
		}
		return marchingOrders;
	}

	/**
	 * Here you will calculate the cumulative average rewards for your testing episodes. If you have just
	 * finished a set of test episodes you will call out testEpisode.
	 *
	 * It is also a good idea to save your weights with the saveWeights function.
	 */
	@Override
	public void terminalStep(State.StateView stateView, History.HistoryView historyView) {
		currentPhaseEpisodeCount++;
		if(inEvaluationEpisode){
			if(currentPhaseEpisodeCount == NUM_EVALUATING_EPISODES){
				averageRewards.add(cumulativeReward/NUM_EVALUATING_EPISODES);
				cumulativeReward = 0.0;
				printTestData(averageRewards);
				currentPhaseEpisodeCount = 0;
				inEvaluationEpisode = false;
			}
		} else if(currentPhaseEpisodeCount == NUM_LEARNING_EPISODES){
			inEvaluationEpisode = true;
			currentPhaseEpisodeCount = 0;
			cumulativeReward = 0.0;
		}
		saveWeights(weights);
		currentEpisode++;
		if(currentEpisode > numEpisodes){
			System.exit(0);
		}
	}

	/**
	 * Calculate the updated weights for this agent. 
	 * @param oldWeights Weights prior to update
	 * @param oldFeatures Features from (s,a)
	 * @param totalReward Cumulative discounted reward for this footman.
	 * @param stateView Current state of the game.
	 * @param historyView History of the game up until this point
	 * @param footmanId The footman we are updating the weights for
	 * @return The updated weight vector.
	 */
	private double[] updateWeights(double[] oldWeights, double[] oldFeatures, double totalReward, State.StateView stateView, History.HistoryView historyView, int footmanId) {
		double[] newWeights = new double[NUM_FEATURES];
		int toAttack = getArgMaxForQ(stateView, historyView, footmanId);
		double QValue = calcQValue(stateView, historyView, footmanId, toAttack);
		double previousQValue = calcQValueGivenFeatures(oldFeatures);
		for(int i = 0; i < NUM_FEATURES; i++){
			newWeights[i] = oldWeights[i] - LEARNING_RATE * (-(totalReward + (GAMMA * QValue) - previousQValue) * calcFeatureValue(i, stateView, historyView, footmanId, toAttack));
		}
		return newWeights;
	}

	/**
	 * Given a footman and the current state and history of the game select the enemy that this unit should
	 * attack. This is where you would do the epsilon-greedy action selection.
	 *
	 * @param stateView Current state of the game
	 * @param historyView The entire history of this episode
	 * @param attackerId The footman that will be attacking
	 * @return The enemy footman ID this unit should attack
	 */
	private int selectAction(State.StateView stateView, History.HistoryView historyView, int attackerId) {
		Double decider = random.nextDouble();
		if(decider > 1 - EPSILON || inEvaluationEpisode){
			return getArgMaxForQ(stateView, historyView, attackerId);
		} else {
			return enemyFootmen.get(random.nextInt(enemyFootmen.size()));
		}
	}

	private int getArgMaxForQ(State.StateView stateView, History.HistoryView historyView, int attackerId) {
		int toAttackId = -1;
		double max = Double.MIN_VALUE;
		for(Integer enemyId : enemyFootmen){
			double possible = calcQValue(stateView, historyView, attackerId, enemyId);
			if(possible > max){
				max = possible;
				toAttackId = enemyId;  
			}
		}    	
		return toAttackId;
	}

	/**
	 * @param stateView The current state of the game.
	 * @param historyView History of the episode up until this turn.
	 * @param footmanId The footman ID you are looking for the reward from.
	 * @return The current reward
	 */
	private double calculateReward(State.StateView stateView, History.HistoryView historyView, int footmanId) {
		double reward = -0.1;
		int previousTurnNumber = stateView.getTurnNumber() - 1;

		for(DamageLog damageLog : historyView.getDamageLogs(previousTurnNumber)) {
			if(damageLog.getAttackerController() == playernum && damageLog.getAttackerID() == footmanId){
				reward = reward + damageLog.getDamage();
			} else if(damageLog.getAttackerController() == ENEMY_PLAYERNUM && damageLog.getDefenderID() == footmanId){
				reward = reward - damageLog.getDamage();
			}
//			System.out.println("Damage entry: Defending player: " + damageLog.getDefenderController() + " defending unit: " + damageLog.getDefenderID() + " attacking player: " + damageLog.getAttackerController() + "attacking unit: " + damageLog.getAttackerID());
		}

		for(DeathLog deathLog : historyView.getDeathLogs(previousTurnNumber)){
			if(deathLog.getController() == ENEMY_PLAYERNUM && thisFootmanWasAttackingTheDeadGuy(footmanId, deathLog, historyView, previousTurnNumber)){
				reward = reward + 100;
			} else if(deathLog.getDeadUnitID() == footmanId) {
				reward = reward - 100;
			}
		}

		return reward;
	}

	private boolean thisFootmanWasAttackingTheDeadGuy(int footmanId, DeathLog deathLog, History.HistoryView historyView, int previousTurnNumber) {
		try {
			Map<Integer, ActionResult> actionResults = historyView.getCommandFeedback(footmanId, previousTurnNumber);   	
			if(actionResults.containsKey(footmanId) && actionResults.get(footmanId).getFeedback().equals(ActionFeedback.COMPLETED)){
				TargetedAction thing = (TargetedAction) actionResults.get(footmanId).getAction() ;
				return thing.getTargetId() == deathLog.getDeadUnitID();
			}
			return false;
		} catch(Exception e){
			return false;
		}
	}

	/**
	 *
	 * @param stateView Current SEPIA state
	 * @param historyView Episode history up to this point in the game
	 * @param attackerId Your footman. The one doing the attacking.
	 * @param defenderId An enemy footman that your footman would be attacking
	 * @return The approximate Q-value
	 */
	private double calcQValue(State.StateView stateView, History.HistoryView historyView, int attackerId, int defenderId) {
		double[] featureValues = calculateFeatureVector(stateView, historyView, attackerId, defenderId);
		return calcQValueGivenFeatures(featureValues);
	}

	private double calcQValueGivenFeatures(double[] featureValues) { 
		double qValue = 0;
		for(int i = 0; i < NUM_FEATURES; i++){
			qValue = qValue + weights[i] * featureValues[i];
		}
		return qValue;
	}


	/**
	 * @param stateView Current state of the SEPIA game
	 * @param historyView History of the game up until this turn
	 * @param attackerId Your footman. The one doing the attacking.
	 * @param defenderId An enemy footman. The one you are considering attacking.
	 * @return The array of feature function outputs.
	 */
	private double[] calculateFeatureVector(State.StateView stateView, History.HistoryView historyView, int attackerId, int defenderId) {
		double[] featureValues = new double[NUM_FEATURES];

		for(int i = 0; i < NUM_FEATURES; i++){
			featureValues[i] = calcFeatureValue(i, stateView, historyView, attackerId, defenderId);
		}

		return featureValues;
	}

	private double calcFeatureValue(int featureId, State.StateView stateView, History.HistoryView historyView, int attackerId, int defenderId){
		double result = 0;
		switch(featureId){
		case 0:
			result = 1;
			break;
		case 1:
			result = isClosestEnemy(stateView, historyView, attackerId, defenderId);
			break;
		}
		return result;
	}
	
//	Is e my closest enemy in terms of Chebyshev distance?” Some useful features are “coordination” features such as “How
//	many other footmen are currently attacking e?”. Some others are “reflective” features such as “Is e an
//	enemy that is currently attacking me?” Yet other features could be things like “What is the ratio of the
//		hitpoints of e to me?”

	private double isClosestEnemy(StateView stateView, HistoryView historyView, int attackerId, int defenderId) {
		int closestEnemy = getClosestEnemy(stateView, historyView, attackerId);
		if(closestEnemy == defenderId ){
			return 1;
		}
		return 0;
	}
	

	private int getClosestEnemy(StateView stateView, HistoryView historyView, int attackerId) {
		int closestEnemyId = -1;
		double closestDistance = Double.MAX_VALUE;
		UnitView attacker = stateView.getUnit(attackerId);
		int attackerX = attacker.getXPosition();
		int attackerY = attacker.getYPosition();
		for(Integer enemyId : enemyFootmen){
			UnitView defender = stateView.getUnit(enemyId);
			int defenderX = defender.getXPosition();
			int defenderY = defender.getYPosition();
			double distance = Math.max(Math.abs(attackerX - defenderX), Math.abs(attackerY - defenderY));
			if(distance < closestDistance){
				closestDistance = distance;
				closestEnemyId = enemyId;
			}
		}
		return closestEnemyId;
	}
	
	

	/**
	 * DO NOT CHANGE THIS!
	 *
	 * Prints the learning rate data described in the assignment. Do not modify this method.
	 *
	 * @param averageRewards List of cumulative average rewards from test episodes.
	 */
	public void printTestData (List<Double> averageRewards) {
		System.out.println("");
		System.out.println("Games Played      Average Cumulative Reward");
		System.out.println("-------------     -------------------------");
		for (int i = 0; i < averageRewards.size(); i++) {
			String gamesPlayed = Integer.toString(10*i);
			String averageReward = String.format("%.2f", averageRewards.get(i));

			int numSpaces = "-------------     ".length() - gamesPlayed.length();
			StringBuffer spaceBuffer = new StringBuffer(numSpaces);
			for (int j = 0; j < numSpaces; j++) {
				spaceBuffer.append(" ");
			}
			System.out.println(gamesPlayed + spaceBuffer.toString() + averageReward);
		}
		System.out.println("");
	}

	/**
	 * DO NOT CHANGE THIS!
	 *
	 * This function will take your set of weights and save them to a file. Overwriting whatever file is
	 * currently there. You will use this when training your agents. You will include the output of this function
	 * from your trained agent with your submission.
	 *
	 * Look in the agent_weights folder for the output.
	 *
	 * @param weights Array of weights
	 */
	public void saveWeights(Double[] weights) {
		File path = new File("agent_weights/weights.txt");
		// create the directories if they do not already exist
		path.getAbsoluteFile().getParentFile().mkdirs();

		try {
			// open a new file writer. Set append to false
			BufferedWriter writer = new BufferedWriter(new FileWriter(path, false));

			for (double weight : weights) {
				writer.write(String.format("%f\n", weight));
			}
			writer.flush();
			writer.close();
		} catch(IOException ex) {
			System.err.println("Failed to write weights to file. Reason: " + ex.getMessage());
		}
	}

	/**
	 * DO NOT CHANGE THIS!
	 *
	 * This function will load the weights stored at agent_weights/weights.txt. The contents of this file
	 * can be created using the saveWeights function. You will use this function if the load weights argument
	 * of the agent is set to 1.
	 *
	 * @return The array of weights
	 */
	public Double[] loadWeights() {
		File path = new File("agent_weights/weights.txt");
		if (!path.exists()) {
			System.err.println("Failed to load weights. File does not exist");
			return null;
		}

		try {
			BufferedReader reader = new BufferedReader(new FileReader(path));
			String line;
			List<Double> weights = new LinkedList<>();
			while((line = reader.readLine()) != null) {
				weights.add(Double.parseDouble(line));
			}
			reader.close();

			return weights.toArray(new Double[weights.size()]);
		} catch(IOException ex) {
			System.err.println("Failed to load weights from file. Reason: " + ex.getMessage());
		}
		return null;
	}

	@Override
	public void savePlayerData(OutputStream outputStream) {

	}

	@Override
	public void loadPlayerData(InputStream inputStream) {

	}
}
