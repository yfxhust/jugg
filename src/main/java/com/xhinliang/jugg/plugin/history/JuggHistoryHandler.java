package com.xhinliang.jugg.plugin.history;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;
import com.xhinliang.jugg.context.CommandContext;
import com.xhinliang.jugg.exception.JuggRuntimeException;
import com.xhinliang.jugg.handler.IJuggHandler;
import com.xhinliang.jugg.parse.IJuggEvalKiller;
import com.xhinliang.jugg.util.FunctionUtils;

/**
 * @author xhinliang <xhinliang@gmail.com>
 * Created on 2019-03-21
 */
public class JuggHistoryHandler implements IJuggHandler {

    private final JuggHistoryService historyService = JuggHistoryServiceImpl.getInstance();

    private final List<String> lastQueryResult = new CopyOnWriteArrayList<>();

    private final IJuggEvalKiller evalKiller;

    public JuggHistoryHandler(IJuggEvalKiller evalKiller) {
        this.evalKiller = evalKiller;
    }

    public void handle(CommandContext context) {
        String result = this.generateResult(context);
        if (result != null) {
            context.setResult(result);
            context.setShouldEnd(true);
        }
    }

    @Nullable
    public String generateResult(CommandContext context) {
        String command = context.getCommand();
        String historyCommand = getHistoryCommand(command);
        if (historyCommand != null) {
            CommandContext mockContext = new CommandContext(context.getJuggUser(), historyCommand);
            return "\n" + historyCommand + "\n"
                    + MoreObjects.firstNonNull(FunctionUtils.getJsonCatching(() -> this.evalKiller.eval(mockContext)), "null");
        } else if (command.startsWith("history ")) {
            String[] spliced = command.split(" ");
            if (spliced.length == 2) {
                return handleSearchHistory(context.getJuggUser().getUserName(), spliced);
            } else {
                throw new JuggRuntimeException("[system] history syntax error!");
            }
        } else if (command.equals("history")) {
            return handleAllHistory(context.getJuggUser().getUserName());
        } else {
            historyService.addHistory(context.getJuggUser().getUserName(), command);
            return null;
        }
    }

    @Nullable
    private String getHistoryCommand(String command) {
        String pattern = "^!(\\d+)$";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(command);
        if (m.find()) {
            // CHECKSTYLE:OFF
            String indexStr = m.group(1);
            int index = Integer.parseInt(indexStr);
            List<String> tempCommands = new ArrayList<>(lastQueryResult);
            return tempCommands.size() > index ? tempCommands.get(index) : null;
        }
        return null;
    }

    private String handleAllHistory(String username) {
        List<String> tempLastQueryResult = historyService.query(username, "");
        return buildResultAndUpdate(tempLastQueryResult);
    }

    private String handleSearchHistory(String username, String[] spliced) {
        String keyword = spliced[1];
        List<String> tempLastQueryResult = historyService.query(username, keyword);
        return buildResultAndUpdate(tempLastQueryResult);
    }

    private String buildResultAndUpdate(List<String> tempLastQueryResult) {
        lastQueryResult.clear();
        lastQueryResult.addAll(tempLastQueryResult);
        StringBuilder sb = new StringBuilder("list of your history commands, use !{{index}} to call it again.\n");
        for (int i = 0; i < lastQueryResult.size(); ++i) {
            sb.append(i).append(") ").append(lastQueryResult.get(i));
            if (i != lastQueryResult.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
