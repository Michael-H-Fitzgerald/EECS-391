package edu.cwru.sepia.agent.planner.actions;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.planner.GameState;
import edu.cwru.sepia.agent.planner.Position;

public class MoveAction implements StripsAction {
	int playerId;
	Position destination;
	
	public MoveAction(int playerId, Position destination){
		this.playerId = playerId;
		this.destination = destination;
	}
	
	@Override
	public boolean preconditionsMet(GameState state) {
		Position current = state.getPeasantPosition(playerId);
		return current != destination && !state.isOccupied(destination);
	}

	@Override
	public GameState apply(GameState state) {
		state.movePosition(this, playerId, destination);
		return state;
	}

	@Override
	public Action createSepiaAction() {
		return Action.createCompoundMove(playerId, destination.x, destination.y);
	}

}
