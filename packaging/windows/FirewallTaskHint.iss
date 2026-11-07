const
  LB_GETITEMRECT = $0198;
  TTM_ADDTOOLW = $0432;
  TTM_SETMAXTIPWIDTH = $0418;
  TTF_SUBCLASS = $0010;
  TTS_NOPREFIX = $0002;
  WS_EX_TOPMOST = $00000008;
  WS_POPUP = $80000000;

type
  TFirewallToolInfo = record
    Size, Flags: Cardinal;
    Window: HWND;
    ID: THandle;
    Bounds: TRect;
    Instance: THandle;
    Text: THandle;
    Data: THandle;
    Reserved: THandle;
  end;

var
  FirewallHintWindow: HWND;
  FirewallHintText: THandle;

function ReadTaskItemRect(Window: HWND; Message: Cardinal; Index: THandle;
  var Bounds: TRect): THandle;
  external 'SendMessageW@user32.dll stdcall setuponly';
function AddTooltipTool(Window: HWND; Message: Cardinal; Param: THandle;
  var Tool: TFirewallToolInfo): THandle;
  external 'SendMessageW@user32.dll stdcall setuponly';
function CreateTaskTooltip(ExStyle: Cardinal; ClassName, WindowName: String;
  Style: Cardinal; X, Y, Width, Height: Integer; Parent: HWND;
  Menu, Instance, Param: THandle): HWND;
  external 'CreateWindowExW@user32.dll stdcall setuponly';
function DestroyTaskTooltip(Window: HWND): Boolean;
  external 'DestroyWindow@user32.dll stdcall setuponly';
function AllocateHintText(Flags: Cardinal; Bytes: THandle): THandle;
  external 'LocalAlloc@kernel32.dll stdcall setuponly';
function FreeHintText(Text: THandle): THandle;
  external 'LocalFree@kernel32.dll stdcall setuponly';
function CopyHintText(Target: THandle; const Text: String; Capacity: Integer): THandle;
  external 'lstrcpynW@kernel32.dll stdcall setuponly';

<event('DeinitializeSetup')>
procedure ClearFirewallTaskHint;
begin
  if FirewallHintWindow <> 0 then
    DestroyTaskTooltip(FirewallHintWindow);
  if FirewallHintText <> 0 then
    FreeHintText(FirewallHintText);
  FirewallHintWindow := 0;
  FirewallHintText := 0;
end;

procedure UpdateFirewallTaskHint(PageID: Integer);
var
  Tasks: TNewCheckListBox;
  I, Capacity: Integer;
  Tool: TFirewallToolInfo;
  Caption, Hint: String;
begin
  ClearFirewallTaskHint;
  if (PageID <> wpSelectTasks) or WizardSilent then
    Exit;

  Tasks := WizardForm.TasksList;
  Caption := CustomMessage('FirewallAccess');
  for I := 0 to Tasks.Items.Count - 1 do
  begin
    if Tasks.ItemCaption[I] <> Caption then
      Continue;
    if ReadTaskItemRect(Tasks.Handle, LB_GETITEMRECT, I, Tool.Bounds) <> 0 then
      Exit;
    Hint := CustomMessage('FirewallAccessHint');
    Capacity := Length(Hint) + 1;
    FirewallHintText := AllocateHintText(0, Capacity * 2);
    if FirewallHintText = 0 then
      Exit;
    CopyHintText(FirewallHintText, Hint, Capacity);
    FirewallHintWindow := CreateTaskTooltip(WS_EX_TOPMOST, 'tooltips_class32',
      '', WS_POPUP or TTS_NOPREFIX, 0, 0, 0, 0, Tasks.Handle, 0, 0, 0);
    if FirewallHintWindow <> 0 then
    begin
      Tool.Size := SizeOf(Tool);
      Tool.Flags := TTF_SUBCLASS;
      Tool.Window := Tasks.Handle;
      Tool.ID := 1;
      Tool.Instance := 0;
      Tool.Text := FirewallHintText;
      Tool.Data := 0;
      Tool.Reserved := 0;
      SendMessage(FirewallHintWindow, TTM_SETMAXTIPWIDTH, 0, ScaleX(320));
      if AddTooltipTool(FirewallHintWindow, TTM_ADDTOOLW, 0, Tool) <> 0 then
        Exit;
    end;
    ClearFirewallTaskHint;
    Exit;
  end;
end;
