package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Position;
import edu.cwru.sepia.util.Direction;

public class DepositAction implements StripsAction {
	int peasantId;
	Position peasantPos;
	Position townHallPos;
	
	public DepositAction(int peasantId, GameState state){
		this.peasantId = peasantId;
		this.peasantPos = state.getPeasantPosition(peasantId);
		this.townHallPos = state.getTownHallPosition();
	}
	
	@Override
	public boolean preconditionsMet(GameState state) {
		return state.playerIsHolding(peasantId) && peasantPos.isAdjacent(townHallPos);
	}

	@Override
	public GameState apply(GameState state) {
		state.deposit(this, peasantId);
		return state;
	}

	@Override
	public Action createSepiaAction() {
		Direction townhallDirection = peasantPos.getDirection(townHallPos);
		return Action.createPrimitiveDeposit(peasantId, townhallDirection);
	}

}
