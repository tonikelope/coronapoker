const
  LB_GETITEMRECT = $0198;
  TTM_ADDTOOLW = $0432;
  TTM_SETMAXTIPWIDTH = $0418;
  TTF_SUBCLASS = $0010;
  TTS_ALWAYSTIP = $0001;
  TTS_NOPREFIX = $0002;
  WS_EX_TOPMOST = $00000008;
  WS_POPUP = $80000000;
  SWP_NOSIZE = $0001;
  SWP_NOMOVE = $0002;
  SWP_NOACTIVATE = $0010;

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
procedure InitTooltipControls;
  external 'InitCommonControls@comctl32.dll stdcall setuponly';
function SetTooltipPosition(Window, InsertAfter: HWND; X, Y, Width,
  Height: Integer; Flags: Cardinal): Boolean;
  external 'SetWindowPos@user32.dll stdcall setuponly';
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
    if (ReadTaskItemRect(Tasks.Handle, LB_GETITEMRECT, I, Tool.Bounds) = THandle(-1)) or
      (Tool.Bounds.Bottom <= Tool.Bounds.Top) then
      Exit;
    Hint := CustomMessage('FirewallAccessHint');
    Capacity := Length(Hint) + 1;
    FirewallHintText := AllocateHintText(0, Capacity * 2);
    if FirewallHintText = 0 then
      Exit;
    CopyHintText(FirewallHintText, Hint, Capacity);
    InitTooltipControls;
    { The tool is an owner-drawn row inside a child list box. The tip must
      work even when that child is not itself the active window. }
    FirewallHintWindow := CreateTaskTooltip(WS_EX_TOPMOST, 'tooltips_class32',
      '', WS_POPUP or TTS_NOPREFIX or TTS_ALWAYSTIP, 0, 0, 0, 0,
      WizardForm.Handle, 0, 0, 0);
    if FirewallHintWindow <> 0 then
    begin
      SetTooltipPosition(FirewallHintWindow, HWND(-1), 0, 0, 0, 0,
        SWP_NOSIZE or SWP_NOMOVE or SWP_NOACTIVATE);
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
    Log('Could not attach the Firewall task tooltip.');
    ClearFirewallTaskHint;
    Exit;
  end;
end;
