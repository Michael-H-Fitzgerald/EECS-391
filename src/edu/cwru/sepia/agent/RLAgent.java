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
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;

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

    public static final int NUM_FEATURES = 1;

    public final int numEpisodes;
    public int currentEpisode = 0;
    public int currentPhaseEpisodeCount = 0;
    public boolean inEvaluationEpisode = false;
    public List<Double> averageRewards = new ArrayList<Double>(10);
    
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
     * You will need to calculate the reward at each step and update your totals. You will also need to TODO
     * check if an event has occurred. If it has then you will need to update your weights and select a new action.
     *
     * You should also check for completed actions using the history view. Obviously you never want a footman just
     * sitting around doing nothing (the enemy certainly isn't going to stop attacking). So at the minimum you will
     * have an event whenever one your footmen's targets is killed or an action fails. Actions may fail if the target
     * is surrounded or the unit cannot find a path to the unit. To get the action results from the previous turn
     * you can do something similar to the following. Please be aware that on the first turn you should not call this
     *
     *
     * @return New actions to execute or nothing if an event has not occurred.
     */
	@Override
	public Map<Integer, Action> middleStep(State.StateView stateView, History.HistoryView historyView) {
		int previousTurnNumber = stateView.getTurnNumber() - 1;
		Map<Integer, Action> marchingOrders = new HashMap<>();
		if(previousTurnNumber > 0){
			for(DeathLog deathLog : historyView.getDeathLogs(previousTurnNumber)){
				if(deathLog.getController() == ENEMY_PLAYERNUM){
					enemyFootmen.remove(((Integer) deathLog.getDeadUnitID()));
				} else {
					myFootmen.remove(((Integer) deathLog.getDeadUnitID()));
				}
				System.out.println("Player: " + deathLog.getController() + " unit: " + deathLog.getDeadUnitID());    			
			}

			Map<Integer, ActionResult> actionResults = historyView.getCommandFeedback(playernum, previousTurnNumber);
			for(ActionResult result : actionResults.values()) {
				System.out.println(result.toString());
				if(result.getFeedback().equals(ActionFeedback.FAILED)){
					marchingOrders.put(result.getAction().getUnitId(), result.getAction());
				}
			}
			
			for(Integer attackerId : myFootmen){
				if(!marchingOrders.containsKey(attackerId)){
					if(!actionResults.containsKey(attackerId) || actionResults.get(attackerId).getFeedback().equals(ActionFeedback.COMPLETED)){
						int defenderId = 1;
						
						double[] oldWeights = new double[NUM_FEATURES];
						for(int i = 0; i < NUM_FEATURES; i++){
							oldWeights[i] = weights[i];
						}
						double[] newWeights = 
									updateWeights(	oldWeights, 
													calculateFeatureVector(stateView, historyView, attackerId, defenderId),
													calculateReward(stateView, historyView, attackerId),
													stateView,
													historyView,
													attackerId);
						for(int i = 0; i < NUM_FEATURES; i++){
							weights[i] = newWeights[i];
						}
						int enemyId = selectAction(stateView, historyView, attackerId);
						Action action = Action.createCompoundAttack(attackerId, enemyId);
						marchingOrders.put(attackerId, action);
					}
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
    		//averageRewards.add(calculateReward(stateView, historyView, footmanId)) TODO
    		if(currentPhaseEpisodeCount == NUM_EVALUATING_EPISODES){
    			printTestData(averageRewards);
    			currentPhaseEpisodeCount = 0;
    			averageRewards = new ArrayList<Double>(10);
    		}
    	} else if(currentPhaseEpisodeCount == NUM_LEARNING_EPISODES){
    		inEvaluationEpisode = true;
			currentPhaseEpisodeCount = 0;
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
    		newWeights[i] = oldWeights[i] - LEARNING_RATE * (-(totalReward + (GAMMA * QValue) - previousQValue) * calcFeatureValue(i));
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
    	if(decider > 1 - EPSILON){
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
     * Given the current state and the footman in question calculate the reward received on the last turn.
     * This is where you will check for things like Did this footman take or give damage? Did this footman die
     * or kill its enemy. Did this footman start an action on the last turn? See the assignment description
     * for the full list of rewards.
     *
     * Remember that you will need to discount this reward based on the timestep it is received on. See
     * the assignment description for more details.
     *
     *
     * You will do something similar for the deaths. See the middle step documentation for a snippet
     * showing how to use the deathLogs.
     *
     * To see if a command was issued you can check the commands issued log.
     *
     * Map<Integer, Action> commandsIssued = historyView.getCommandsIssued(playernum, lastTurnNumber);
     * for (Map.Entry<Integer, Action> commandEntry : commandsIssued.entrySet()) {
     *     System.out.println("Unit " + commandEntry.getKey() + " was command to " + commandEntry.getValue().toString);
     * }
     *
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
    		System.out.println("Damage entry: Defending player: " + damageLog.getDefenderController() + " defending unit: " + damageLog.getDefenderID() +
    				" attacking player: " + damageLog.getAttackerController() + "attacking unit: " + damageLog.getAttackerID());
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
    	Map<Integer, ActionResult> actionResults = historyView.getCommandFeedback(footmanId, previousTurnNumber);   	
    	if(actionResults.containsKey(footmanId) && actionResults.get(footmanId).getFeedback().equals(ActionFeedback.COMPLETED)){
    		TargetedAction thing = (TargetedAction) actionResults.get(footmanId).getAction() ;
    		return thing.getTargetId() == deathLog.getDeadUnitID();
       	}
    	return false; 
	}

	/**
     * Calculate the Q-Value for a given state action pair. The state in this scenario is the current
     * state view and the history of this episode. The action is the attacker and the enemy pair for the
     * SEPIA attack action.
     *
     * This returns the Q-value according to your feature approximation. This is where you will calculate
     * your features and multiply them by your current weights to get the approximate Q-value.
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
    		featureValues[i] = calcFeatureValue(i);
    	}
    	
    	return featureValues;
    }
    
    private double calcFeatureValue(int featureId){
    	double result = 0;
    	switch(featureId){
    	case 0:
    		result = 1;
    		break;
    	}
    	return result;
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
