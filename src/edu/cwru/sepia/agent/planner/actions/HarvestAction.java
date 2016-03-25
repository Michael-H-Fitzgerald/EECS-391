package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Position;
import edu.cwru.sepia.util.Direction;

public class HarvestAction implements StripsAction {
	int peasantId;
	int resourceId;
	Position peasantPos;
	Position resourcePos;

	public HarvestAction(int peasantId, int resourceId, GameState state){
		this.peasantId = peasantId;
		this.resourceId = resourceId;
		this.peasantPos = state.getPeasantPosition(peasantId);
		this.resourcePos = state.getResourcePosition(resourceId);
	}
	
	@Override
	public boolean preconditionsMet(GameState state) {
		return !state.getPeasantWithId(peasantId).isCarrying() && peasantPos.isAdjacent(resourcePos) && state.hasResources(resourceId);
	}

	@Override
	public GameState apply(GameState state) {
		state.harvest(this, peasantId, resourceId);
		return state;
	}

	@Override
	public Action createSepiaAction() {
		Direction resourceDirection = peasantPos.getDirection(resourcePos);
		return Action.createPrimitiveGather(peasantId, resourceDirection);
	}
	
	@Override
	public int getPeasantId() {
		return peasantId;	
	}
}
