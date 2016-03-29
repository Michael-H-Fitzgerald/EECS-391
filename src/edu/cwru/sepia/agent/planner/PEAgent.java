package edu.cwru.sepia.agent.planner;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionFeedback;
import edu.cwru.sepia.action.ActionResult;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.agent.planner.actions.StripsAction;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

/**
 * This is an outline of the PEAgent. Implement the provided methods. You may add your own methods and members.
 */
public class PEAgent extends Agent {
	private static final String TOWN_HALL_NAME = "townhall";

	private static final long serialVersionUID = 1L;

    private Stack<StripsAction> plan = null;
    private int TOWN_HALL_ID;

    public PEAgent(int playernum, Stack<StripsAction> plan) {
        super(playernum);
        this.plan = plan;
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView stateView, History.HistoryView historyView) {
    	
        // gets the townhall ID and the peasant ID
        for(int unitId : stateView.getUnitIds(playernum)) {
            Unit.UnitView unit = stateView.getUnit(unitId);
            String unitType = unit.getTemplateView().getName().toLowerCase();
            if(unitType.equals(TOWN_HALL_NAME)) {
                TOWN_HALL_ID = unitId;
            }
        }

        return middleStep(stateView, historyView);
    }

    /**
     * This is where you will read the provided plan and execute it. If your plan is correct then when the plan is empty
     * the scenario should end with a victory. If the scenario keeps running after you run out of actions to execute
     * then either your plan is incorrect or your execution of the plan has a bug.
     *     *
     * For the compound actions you will need to check their progress and wait until they are complete before issuing
     * another action for that unit. If you issue an action before the compound action is complete then the peasant
     * will stop what it was doing and begin executing the new action.
     *
     * To check an action's progress you can call getCurrentDurativeAction on each UnitView. If the Action is null nothing
     * is being executed. If the action is not null then you should also call getCurrentDurativeProgress. If the value is less than
     * 1 then the action is still in progress.
     *
     * Also remember to check your plan's preconditions before executing!
     */
    @Override
    public Map<Integer, Action> middleStep(State.StateView stateView, History.HistoryView historyView) {
    	Map<Integer, Action> actionMap = new HashMap<Integer, Action>();

    	if(plan.isEmpty()){
    		return actionMap;
    	}
    	
    	int previousTurnNumber = stateView.getTurnNumber() - 1;
    	
    	if(previousTurnNumber < 0) {
        	// It is the first turn initialize this assumes there is 
    		// only one peasant at start and so only adds one action
    		addNextAction(actionMap, stateView);
    		return actionMap;
    	}

		Map<Integer, ActionResult> previousActions = historyView.getCommandFeedback(playernum, previousTurnNumber);	
		boolean done = false;
		while(!done){
			if(plan.empty()){
				done = true;
			} else {
				StripsAction next = plan.peek();
				ActionResult previous = previousActions.get(next.getUnitId());
				if(previous != null && previous.getFeedback() == ActionFeedback.FAILED){
					actionMap.put(previous.getAction().getUnitId(), previous.getAction());
				}
				if(actionMap.containsKey(TOWN_HALL_ID)){
					// If we are building don't add the next action
					done = true;
				}
				if(actionMap.containsKey(next.getUnitId()) || (previous != null && previous.getFeedback().ordinal() == ActionFeedback.INCOMPLETE.ordinal())){
					done = true;
				} else {
					if(next.getUnitId() == TOWN_HALL_ID && !actionMap.isEmpty()){ 
						done = true; // Wait a turn to do the building
					} else {
						addNextAction(actionMap, stateView);
					}
				}
			}
		}
    	return actionMap;
    }

    private void addNextAction(Map<Integer, Action> actionMap, State.StateView state) {
    	StripsAction action = plan.pop();
    	Action sepiaAction = null;
    	if(!action.isDirectedAction()){
    		sepiaAction = action.createSepiaAction(null);
    	} else {
    		UnitView peasant = state.getUnit(action.getUnitId());
    		Position peasantPos = new Position(peasant.getXPosition(), peasant.getYPosition());
    		Position destinationPos = action.getPositionForDirection();
    		sepiaAction = action.createSepiaAction(peasantPos.getDirection(destinationPos));
    	}
		actionMap.put(sepiaAction.getUnitId(), sepiaAction);
	}
    
	@Override
    public void terminalStep(State.StateView stateView, History.HistoryView historyView) {

    }

    @Override
    public void savePlayerData(OutputStream outputStream) {

    }

    @Override
    public void loadPlayerData(InputStream inputStream) {

    }
}
