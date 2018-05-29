package pl.itcraft.soma.api.servlets.queue;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pl.itcraft.soma.core.QueueUtils;
import pl.itcraft.soma.core.QueueUtils.PromotingAction;
import pl.itcraft.soma.core.error.ApiException;
import pl.itcraft.soma.core.model.entities.Item;
import pl.itcraft.soma.core.search.SearchDocumentService;
import pl.itcraft.soma.core.service.ItemService;

public class PromotingActionQueueServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private final SearchDocumentService searchDocumentService = new SearchDocumentService();
	private final ItemService itemService = new ItemService();

	private final static Logger logger = Logger.getLogger(PromotingActionQueueServlet.class.getName());

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (QueueUtils.isRequestFromTaskQueue(request, response)) {

			Long userId = Long.parseLong(request.getParameter(QueueUtils.USER_ID_PARAMETER_NAME));
			PromotingAction promotingAction = PromotingAction
					.valueOf(request.getParameter(QueueUtils.PROMOTING_ACTION_PARAMETER_NAME));
			try {

				switch (promotingAction) {
				case START: {
					Long itemId = Long.parseLong(request.getParameter(QueueUtils.ITEM_ID_PARAMETER_NAME));
					Item item = itemService.getItem(itemId);
					searchDocumentService.proceedStartPromoting(item, userId);
					break;
				}
				case STOP: {
					Long itemId = Long.parseLong(request.getParameter(QueueUtils.ITEM_ID_PARAMETER_NAME));
					Item item = itemService.getItem(itemId);
					searchDocumentService.proceedStopPromoting(item, userId);
					break;
				}
				case FRIEND:{
					Long otherUserId = Long.parseLong(request.getParameter(QueueUtils.OTHER_USER_ID_PARAMETER_NAME));
					searchDocumentService.proceedNewFollowerPromoting(otherUserId, userId, true);
					break;
				}
				case FOLLOW: {
					Long otherUserId = Long.parseLong(request.getParameter(QueueUtils.OTHER_USER_ID_PARAMETER_NAME));
					searchDocumentService.proceedNewFollowerPromoting(otherUserId, userId, false);
					break;
				}
				case UNFRIEND:
					Long unfriendId = Long.parseLong(request.getParameter(QueueUtils.OTHER_USER_ID_PARAMETER_NAME));
					searchDocumentService.proceedUnfriendPromoting(userId, unfriendId);
					break;
				case UNFOLLOW:
					Long unfollowId = Long.parseLong(request.getParameter(QueueUtils.OTHER_USER_ID_PARAMETER_NAME));
					searchDocumentService.proceedUnfollowPromoting(userId, unfollowId);
					break;
				default:
					break;
				}
			} catch (ApiException e) {
				logger.log(Level.WARNING, "api exception", e);
			} catch (Exception e) {
				logger.log(Level.WARNING, "unknown error", e);
			}
		}
	}
}
